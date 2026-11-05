package dev.vestiges.dragon;

import dev.vestiges.Vestiges;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.VestigesSystem;
import dev.vestiges.data.VestigesData;
import dev.vestiges.feats.FeatService;
import dev.vestiges.inventory.InventoryService;
import dev.vestiges.level.LevelService;
import dev.vestiges.mixin.EnderDragonFightAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.phys.AABB;

/**
 * The first dragons (Act I, dragons 1 to 6). The world's kill count and the active fight are saved on the overworld, what each player has been
 * rewarded for on the player, and each dragon in the End carries a {@link DragonMark}. Vanilla's own End fight does the rest: the 4 crystals on
 * the portal rim summon, and Vestiges only decides which dragon comes (kill count plus one), who may summon it, how tough it is and who is rewarded.
 * The whole system sits behind the Dragons switch: off, the End is vanilla and nothing here runs.
 */
public final class DragonService {
	public static final AttachmentType<DragonWorld> WORLD = AttachmentRegistry.create(Vestiges.id("dragons"),
		builder -> builder.persistent(DragonWorld.CODEC));
	public static final AttachmentType<PlayerDragons> PLAYER = AttachmentRegistry.create(Vestiges.id("dragons_player"),
		builder -> builder.persistent(PlayerDragons.CODEC).copyOnDeath());
	public static final AttachmentType<DragonMark> MARK = AttachmentRegistry.create(Vestiges.id("dragon_mark"),
		builder -> builder.persistent(DragonMark.CODEC));

	/** The player who last put an end crystal down, and when (the safety net behind the crystal item's own check). */
	private static UUID lastPlacer;
	private static long lastPlacedAt = -10;
	/** How many summonings the gate has refused since the server started (the automatic checks read it). */
	public static int refusals;

	private DragonService() {
	}

	public static boolean enabled() {
		return ConfigStore.server().enabled(VestigesSystem.DRAGONS);
	}

	public static void register() {
		// Touched so the attachment types exist before any world, player or entity data loads.
		java.util.Objects.requireNonNull(WORLD);
		java.util.Objects.requireNonNull(PLAYER);
		java.util.Objects.requireNonNull(MARK);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof EnderDragon dragon) {
				onDragonLoad(level, dragon);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(DragonService::tick);
	}

	// ---- saved state -------------------------------------------------------------------------------------------------

	public static DragonWorld world(MinecraftServer server) {
		DragonWorld w = server.overworld().getAttached(WORLD);
		return w == null ? DragonWorld.EMPTY : w;
	}

	public static void save(MinecraftServer server, DragonWorld world) {
		server.overworld().setAttached(WORLD, world);
	}

	public static PlayerDragons player(ServerPlayer p) {
		PlayerDragons d = p.getAttached(PLAYER);
		return d == null ? PlayerDragons.EMPTY : d;
	}

	public static void setPlayer(ServerPlayer p, PlayerDragons d) {
		p.setAttached(PLAYER, d);
	}

	/** Ender chest pages this player has unlocked: 1 to start, one more for every dragon they were rewarded for. */
	public static int pages(ServerPlayer p, int total) {
		return DragonRules.pages(player(p).count(), total);
	}

	/** The highest dragon number this player was rewarded for (0 = none): what the dragon feats count. */
	public static int best(ServerPlayer p) {
		return player(p).best();
	}

	// ---- the dragons -------------------------------------------------------------------------------------------------

	/** Every dragon of the datapacks, in order of number. */
	public static List<DragonDef> defs() {
		return VestigesData.DRAGONS.all().values().stream().sorted(Comparator.comparingInt(DragonDef::number)).toList();
	}

	/** The dragon with this number, or null (after dragon 6 summoning gives a plain respawn). */
	public static DragonDef def(int number) {
		for (DragonDef d : VestigesData.DRAGONS.all().values()) {
			if (d.number() == number) {
				return d;
			}
		}
		return null;
	}

	public static Identifier id(DragonDef def) {
		for (var e : VestigesData.DRAGONS.all().entrySet()) {
			if (e.getValue() == def) {
				return e.getKey();
			}
		}
		return null;
	}

	public static DragonMark mark(Entity dragon) {
		return dragon == null ? null : dragon.getAttached(MARK);
	}

	/** The portal the fight is centred on (the exit portal, or the fight's origin before one exists). */
	public static BlockPos portalOf(EnderDragonFight fight) {
		EnderDragonFightAccessor a = (EnderDragonFightAccessor) fight;
		BlockPos p = a.vestiges$portal();
		return p != null ? p : a.vestiges$origin();
	}

	// ---- summoning ---------------------------------------------------------------------------------------------------

	private static boolean summonable(EnderDragonFight fight) {
		EnderDragonFightAccessor a = (EnderDragonFightAccessor) fight;
		return a.vestiges$killed() && a.vestiges$stage() == null;
	}

	private static String refusal(DragonDef def, int have) {
		return "The crystals refuse. Dragon " + def.number() + ", " + def.name() + ", needs a summoner of level " + def.gate() + " (you are level " + have
			+ "). Nothing was used.";
	}

	/**
	 * True when this player's end crystal would be the 4th on the portal rim, and so call the next dragon, but their level is below the gate. The
	 * crystal is then not placed and not used up. Called before the crystal item does anything.
	 */
	public static boolean refuses(ServerLevel level, ServerPlayer player, BlockPos clicked) {
		if (!enabled() || !level.dimension().equals(Level.END)) {
			return false;
		}
		lastPlacer = player.getUUID();
		lastPlacedAt = level.getGameTime();
		EnderDragonFight fight = level.getDragonFight();
		if (fight == null || !summonable(fight)) {
			return false;
		}
		BlockPos portal = ((EnderDragonFightAccessor) fight).vestiges$portal();
		BlockState base = level.getBlockState(clicked);
		if (portal == null || !base.is(Blocks.OBSIDIAN) && !base.is(Blocks.BEDROCK) || !level.isEmptyBlock(clicked.above())) {
			return false;
		}
		if (!completesRing(level, portal, clicked)) {
			return false;
		}
		DragonDef def = def(DragonRules.nextNumber(world(level.getServer()).kills()));
		int have = LevelService.get(player).level();
		if (def == null || DragonRules.gateOpen(have, def.gate())) {
			return false;
		}
		tellRefusal(player, refusal(def, have));
		return true;
	}

	/** Would a crystal put on {@code clicked} fill the last free spot of the ring (the 3 others already stand)? */
	private static boolean completesRing(ServerLevel level, BlockPos portal, BlockPos clicked) {
		BlockPos ring = portal.above(1);
		// The box vanilla gives a crystal that is put down on this block.
		AABB mine = new AABB(clicked.getX() - 0.5, clicked.getY() + 1, clicked.getZ() - 0.5, clicked.getX() + 1.5, clicked.getY() + 3, clicked.getZ() + 1.5);
		boolean placedInRing = false;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			AABB spot = new AABB(ring.relative(d, 3));
			if (spot.intersects(mine)) {
				placedInRing = true;
			} else if (level.getEntitiesOfClass(EndCrystal.class, spot).isEmpty()) {
				return false;
			}
		}
		return placedInRing;
	}

	private static void tellRefusal(ServerPlayer player, String text) {
		refusals++;
		player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.RED));
		player.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.RED));
	}

	/** The safety net inside vanilla's respawn: the summoner who just put the crystal down must still reach the gate. */
	public static boolean allowRespawn(EnderDragonFight fight) {
		if (!enabled() || !summonable(fight)) {
			return true;
		}
		ServerLevel level = ((EnderDragonFightAccessor) fight).vestiges$level();
		DragonDef def = def(DragonRules.nextNumber(world(level.getServer()).kills()));
		if (def == null || lastPlacer == null || level.getGameTime() - lastPlacedAt > 2) {
			return true;
		}
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(lastPlacer);
		if (p == null || DragonRules.gateOpen(LevelService.get(p).level(), def.gate())) {
			return true;
		}
		tellRefusal(p, refusal(def, LevelService.get(p).level()));
		return false;
	}

	// ---- a dragon appears --------------------------------------------------------------------------------------------

	private static void onDragonLoad(ServerLevel level, EnderDragon dragon) {
		if (!enabled() || !level.dimension().equals(Level.END) || dragon.getAttached(MARK) != null || dragon.isDeadOrDying()) {
			return;
		}
		MinecraftServer server = level.getServer();
		DragonWorld w = world(server);
		int number = DragonRules.nextNumber(w.kills());
		DragonDef def = def(number);
		if (def == null) {
			return;
		}
		EnderDragonFight fight = level.getDragonFight();
		int party = Math.max(1, fight == null ? 1 : partyAround(level, portalOf(fight)));
		configure(dragon, def, party);
		save(server, w.fighting(number));
		Vestiges.LOGGER.info("Vestiges: dragon {} ({}) appears, health {}", number, def.display(), dragon.getMaxHealth());
	}

	/** Gives the dragon its name, health and mark. */
	private static void configure(EnderDragon dragon, DragonDef def, int party) {
		double max = DragonRules.maxHealth(def.health(), party);
		var health = dragon.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(max);
		}
		dragon.setHealth((float) max);
		dragon.setCustomName(Component.literal(def.display()));
		dragon.setAttached(MARK, new DragonMark(def.number(), party, false));
	}

	/** Players in the End within the party radius of the portal, in survival or adventure. */
	static int partyAround(ServerLevel end, BlockPos portal) {
		int n = 0;
		for (ServerPlayer p : end.players()) {
			if (!p.isSpectator() && !p.isDeadOrDying() && horizontal(p, portal) <= DragonRules.PARTY_RADIUS) {
				n++;
			}
		}
		return n;
	}

	private static double horizontal(Entity e, BlockPos portal) {
		double dx = e.getX() - (portal.getX() + 0.5);
		double dz = e.getZ() - (portal.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0 || !enabled()) {
			return;
		}
		ServerLevel end = server.getLevel(Level.END);
		EnderDragonFight fight = end == null ? null : end.getDragonFight();
		UUID id = fight == null ? null : fight.dragonUUID();
		if (id == null || !(end.getEntity(id) instanceof EnderDragon dragon)) {
			return;
		}
		DragonMark mark = dragon.getAttached(MARK);
		DragonDef def = mark == null ? null : def(mark.number());
		if (def == null || dragon.isDeadOrDying()) {
			return;
		}
		// A bigger party makes it tougher, never the other way round; it keeps the share of health it has left.
		int party = partyAround(end, portalOf(fight));
		if (party > mark.party()) {
			float share = dragon.getHealth() / Math.max(1f, dragon.getMaxHealth());
			var health = dragon.getAttribute(Attributes.MAX_HEALTH);
			if (health != null) {
				health.setBaseValue(DragonRules.maxHealth(def.health(), party));
			}
			dragon.setHealth(share * dragon.getMaxHealth());
			dragon.setAttached(MARK, mark.withParty(party));
		}
	}

	// ---- fighting ----------------------------------------------------------------------------------------------------

	/** The damage multiplier for everything this dragon deals (1 for any other attacker). */
	public static double damageMultiplier(Entity attacker) {
		if (!enabled() || !(attacker instanceof EnderDragon)) {
			return 1.0;
		}
		DragonMark mark = mark(attacker);
		DragonDef def = mark == null ? null : def(mark.number());
		return def == null ? 1.0 : def.damage();
	}

	/**
	 * What a hit on a lore dragon does in the end: nothing when a player who is out of reach in the void lands it, otherwise at most 8% of the dragon's
	 * maximum health. A player's hit also makes them a fighter who is rewarded when it dies.
	 */
	public static float onDragonHit(EnderDragon dragon, DamageSource source, float amount) {
		DragonMark mark = enabled() ? mark(dragon) : null;
		if (mark == null || amount <= 0 || !(dragon.level() instanceof ServerLevel level)) {
			return amount;
		}
		EnderDragonFight fight = level.getDragonFight();
		if (source.getEntity() instanceof ServerPlayer player) {
			if (fight != null && unreachable(player, portalOf(fight))) {
				return 0;
			}
			credit(level, player);
		}
		return DragonRules.capHit(amount, dragon.getMaxHealth());
	}

	/** A player out in the void (far from the portal or far under the island) cannot hurt the dragon: it cannot reach them either. */
	static boolean unreachable(ServerPlayer p, BlockPos portal) {
		return horizontal(p, portal) > DragonRules.PARTY_RADIUS || p.getY() < portal.getY() - DragonRules.VOID_DEPTH;
	}

	/** Remembers a player who hurt the dragon or one of its crystals. */
	public static void credit(ServerLevel level, Entity source) {
		if (!enabled() || !level.dimension().equals(Level.END) || !(source instanceof ServerPlayer p)) {
			return;
		}
		MinecraftServer server = level.getServer();
		DragonWorld w = world(server);
		if (w.active() > 0 && !w.hurt().contains(p.getUUID())) {
			save(server, w.withHurt(p.getUUID()));
		}
	}

	/** An operator's /kill: the dragon dies without counting. */
	public static void killedByCommand(EnderDragon dragon) {
		DragonMark mark = mark(dragon);
		if (mark != null) {
			dragon.setAttached(MARK, mark.killedByCommand());
		}
	}

	// ---- a kill ------------------------------------------------------------------------------------------------------

	/** Vanilla has just marked the dragon as killed: count it and reward everyone who fought and is still near the portal. */
	public static void onKilled(EnderDragonFight fight, EnderDragon dragon) {
		DragonMark mark = enabled() ? mark(dragon) : null;
		if (mark == null || !(dragon.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		DragonWorld w = world(server);
		if (mark.byCommand()) {
			save(server, w.idle());
			return;
		}
		DragonDef def = def(mark.number());
		BlockPos portal = portalOf(fight);
		List<ServerPlayer> winners = new ArrayList<>();
		for (UUID id : w.hurt()) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null && p.level().equals(level) && DragonRules.rewarded(true, Math.sqrt(p.distanceToSqr(portal.getX() + 0.5, p.getY(), portal.getZ() + 0.5)))) {
				winners.add(p);
			}
		}
		save(server, w.withKills(Math.max(w.kills(), mark.number())).idle());
		Vestiges.LOGGER.info("Vestiges: dragon {} killed, {} fighter(s) rewarded", mark.number(), winners.size());
		if (def != null) {
			for (ServerPlayer p : winners) {
				reward(p, def);
			}
		}
	}

	/** What a player gets once for a dragon. */
	static void reward(ServerPlayer p, DragonDef def) {
		if (player(p).has(def.number())) {
			return;
		}
		setPlayer(p, player(p).with(def.number()));
		p.sendSystemMessage(Component.literal("Dragon " + def.number() + " is dead: " + def.display() + ".").withStyle(ChatFormatting.LIGHT_PURPLE));
		InventoryService.sendPages(p);
		FeatService.check(p);
	}
}
