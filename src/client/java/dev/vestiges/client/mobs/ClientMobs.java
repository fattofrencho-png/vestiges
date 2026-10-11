package dev.vestiges.client.mobs;

import dev.vestiges.client.ClientConfigStore;
import dev.vestiges.mobs.MobData;
import dev.vestiges.mobs.MobService;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * How monster levels and elites look on the client (M3a, approved): names with power adjectives, rank colours (white, gold, purple), level
 * colours against your own level, the small health bar over a mob's head (at most 4 at a time), and the short glow when you first spot an
 * elite.
 */
public final class ClientMobs {
	/** Rank colours: Champion white, Elite gold, Warlord purple. */
	private static final int[] RANK_COLORS = {0xFFFFFF, 0xFFFFFF, 0xFFAA00, 0xB65CFF};
	private static final String[] RANK_NAMES = {"", "Champion", "Elite", "Warlord"};
	private static final Map<String, String> ADJECTIVES = Map.ofEntries(
		Map.entry("swift", "Swift"), Map.entry("brute", "Brutal"), Map.entry("armored", "Armored"), Map.entry("burning", "Burning"),
		Map.entry("shove", "Hurling"), Map.entry("shadow", "Shadowy"), Map.entry("caller", "Howling"), Map.entry("volatile", "Volatile"),
		Map.entry("venom", "Venomous"), Map.entry("chilling", "Chilling"), Map.entry("mending", "Mending"), Map.entry("spiked", "Spiked"),
		Map.entry("withering", "Withering"));
	private static final Map<String, String> POWER_TEXT = Map.ofEntries(
		Map.entry("swift", "Swift: +30% speed"), Map.entry("brute", "Brute: +40% damage"),
		Map.entry("armored", "Armored: +10 armor, resists knockback"), Map.entry("burning", "Burning: sets you on fire, immune to fire"),
		Map.entry("shove", "Shove: knocks you far back"), Map.entry("shadow", "Shadow: turns invisible now and then"),
		Map.entry("caller", "Caller: calls 2 helpers at half health"), Map.entry("volatile", "Volatile: explodes when it dies"),
		Map.entry("venom", "Venom: poisons you"), Map.entry("chilling", "Chilling: slows you"),
		Map.entry("mending", "Mending: heals over time"), Map.entry("spiked", "Spiked: reflects 20% of melee damage"),
		Map.entry("withering", "Withering: withers you"));
	private static final long BAR_MS = 5000;
	/** Health bars are drawn at half the size of a vanilla name, and only this many at once so a crowd stays readable. */
	public static final float BAR_SCALE = 0.5f;
	private static final int MAX_BARS = 4;
	private static final String BAR_MARK = "vestiges:health_bar";
	private static final long GLOW_MS = 10000;
	private static final double ELITE_BAR_RANGE_SQ = 32 * 32;

	/** Entity id → when you last hit it (health bar for 5 s). */
	private static final Map<Integer, Long> HITS = new HashMap<>();
	/** Entity id → when you first saw this elite (glow for 10 s). */
	private static final Map<Integer, Long> SPOTTED = new HashMap<>();
	/** The mobs allowed a health bar right now, picked each tick. */
	private static java.util.Set<Integer> barred = java.util.Set.of();

	private ClientMobs() {
	}

	public static MobData data(Entity entity) {
		return entity instanceof Mob mob ? mob.getAttached(MobService.DATA) : null;
	}

	public static void reset() {
		HITS.clear();
		SPOTTED.clear();
		barred = java.util.Set.of();
	}

	public static void onHit(int entityId) {
		long now = System.currentTimeMillis();
		HITS.put(entityId, now);
		if (HITS.size() > 64) {
			HITS.values().removeIf(t -> now - t > BAR_MS);
		}
	}

	public static int rankColor(int rank) {
		return RANK_COLORS[Math.max(0, Math.min(3, rank))];
	}

	/** Level colour against yours: grey 6+ below, green 3-5 below, white within 2, yellow 3-5 above, red 6+ above. */
	public static int levelColor(int mobLevel) {
		Player player = Minecraft.getInstance().player;
		int gap = mobLevel - (player == null ? 1 : Math.max(1, player.experienceLevel));
		if (gap <= -6) {
			return 0x9A9A9A;
		}
		if (gap <= -3) {
			return 0x55FF55;
		}
		if (gap < 3) {
			return 0xFFFFFF;
		}
		return gap < 6 ? 0xFFFF55 : 0xFF5555;
	}

	/** "Swift Venomous Zombie" in the rank colour (the card's banner names the rank); plain mobs keep their own name. */
	public static MutableComponent name(Entity entity, MobData d) {
		MutableComponent out = Component.empty();
		if (d != null && d.elite()) {
			StringBuilder adj = new StringBuilder();
			for (String p : d.powers()) {
				adj.append(ADJECTIVES.getOrDefault(p, p)).append(' ');
			}
			out.append(adj.toString()).append(entity.getName().copy());
			return out.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rankColor(d.rank()))));
		}
		return out.append(entity.getName().copy());
	}

	public static Component levelTag(MobData d) {
		return Component.literal("Lv " + d.level()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(levelColor(d.level()))));
	}

	/** The rank banner on the bigger look-at card. */
	public static Component banner(MobData d) {
		return Component.literal("— " + RANK_NAMES[d.rank()].toUpperCase(java.util.Locale.ROOT) + " —")
			.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rankColor(d.rank()))).withBold(true));
	}

	public static String powerText(String power) {
		return POWER_TEXT.getOrDefault(power, power);
	}

	// ---- health bar over the head ------------------------------------------------------------------------------------

	/** Whether this mob gets a health bar now (picked each tick by {@link #pickBars}). */
	public static boolean showBar(Mob mob) {
		Minecraft mc = Minecraft.getInstance();
		return barred.contains(mob.getId()) && ClientConfigStore.get().healthBars && mc.player != null && !mob.isInvisibleTo(mc.player)
			&& mob.isAlive();
	}

	/**
	 * Picks at most 4 bars: the mob under your crosshair first, then the ones you hit in the last 5 s (latest first), then elites you can see
	 * within 32 blocks (nearest first).
	 */
	private static void pickBars(Minecraft mc) {
		if (mc.level == null || mc.player == null || !ClientConfigStore.get().healthBars) {
			barred = java.util.Set.of();
			return;
		}
		long now = System.currentTimeMillis();
		java.util.List<Mob> hit = new java.util.ArrayList<>();
		for (Map.Entry<Integer, Long> e : HITS.entrySet()) {
			if (now - e.getValue() < BAR_MS && mc.level.getEntity(e.getKey()) instanceof Mob m && m.isAlive()) {
				hit.add(m);
			}
		}
		hit.sort((a, b) -> Long.compare(HITS.get(b.getId()), HITS.get(a.getId())));
		java.util.List<Mob> elites = new java.util.ArrayList<>(mc.level.getEntitiesOfClass(Mob.class, mc.player.getBoundingBox().inflate(32), m -> {
			MobData d = data(m);
			return d != null && d.elite() && m.isAlive() && m.distanceToSqr(mc.player) < ELITE_BAR_RANGE_SQ;
		}));
		elites.sort(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(mc.player)));
		java.util.LinkedHashSet<Integer> picked = new java.util.LinkedHashSet<>();
		Mob looked = mc.crosshairPickEntity instanceof Mob m ? m : null;
		if (looked != null && (hit.contains(looked) || elites.contains(looked) && spotted(looked) && mc.player.hasLineOfSight(looked))) {
			picked.add(looked.getId());
		}
		for (Mob m : hit) {
			if (picked.size() >= MAX_BARS) {
				break;
			}
			picked.add(m.getId());
		}
		for (Mob m : elites) {
			if (picked.size() >= MAX_BARS) {
				break;
			}
			if (spotted(m) && mc.player.hasLineOfSight(m)) {
				picked.add(m.getId());
			}
		}
		barred = picked;
	}

	public static boolean isHealthBar(Component text) {
		return text != null && BAR_MARK.equals(text.getStyle().getInsertion());
	}

	/** Only the bar: red for health left, dark grey for health lost. Marked so it is drawn small. */
	public static Component nameTag(Mob mob) {
		int filled = Math.round(20 * Math.max(0, Math.min(1, mob.getHealth() / Math.max(1, mob.getMaxHealth()))));
		if (mob.getHealth() > 0 && filled == 0) {
			filled = 1;
		}
		return Component.empty().withStyle(Style.EMPTY.withInsertion(BAR_MARK))
			.append(Component.literal("|".repeat(filled)).withStyle(ChatFormatting.RED))
			.append(Component.literal("|".repeat(20 - filled)).withStyle(ChatFormatting.DARK_GRAY));
	}

	// ---- glow when first spotted -----------------------------------------------------------------------------------

	/** Marks an elite as spotted the first time you have it in sight; true while it is spotted. */
	private static boolean spotted(Mob mob) {
		Long seen = SPOTTED.get(mob.getId());
		if (seen != null) {
			return true;
		}
		LivingEntity player = Minecraft.getInstance().player;
		if (player != null && player.hasLineOfSight(mob)) {
			SPOTTED.put(mob.getId(), System.currentTimeMillis());
			return true;
		}
		return false;
	}

	/** Elites glow in their rank colour for 10 s after you first spot them. */
	public static boolean glowing(Entity entity) {
		if (!(entity instanceof Mob mob)) {
			return false;
		}
		MobData d = data(mob);
		if (d == null || !d.elite()) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mob.distanceToSqr(mc.player) < 48 * 48) {
			spotted(mob);
		}
		Long seen = SPOTTED.get(mob.getId());
		return seen != null && System.currentTimeMillis() - seen < GLOW_MS;
	}

	public static int outlineColor(Entity entity, int original) {
		if (entity.getTeam() == null && glowing(entity)) {
			MobData d = data(entity);
			return d != null ? rankColor(d.rank()) : original;
		}
		return original;
	}

	/** Picks the health bars each tick; forgets mobs that are gone once in a while. */
	public static void tick(Minecraft mc) {
		pickBars(mc);
		if (mc.level == null || mc.level.getGameTime() % 200 != 0) {
			return;
		}
		for (Iterator<Integer> it = SPOTTED.keySet().iterator(); it.hasNext(); ) {
			if (mc.level.getEntity(it.next()) == null) {
				it.remove();
			}
		}
		long now = System.currentTimeMillis();
		HITS.values().removeIf(t -> now - t > BAR_MS);
	}
}
