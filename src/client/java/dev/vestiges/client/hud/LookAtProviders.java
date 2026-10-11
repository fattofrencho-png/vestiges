package dev.vestiges.client.hud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/** The look-at HUD's line providers, in display order. Systems call {@link #register} from their client init to add lines. */
public final class LookAtProviders {
	/** Toggle id for the short "New" tag shown the first time you look at something. Handled by the HUD itself. */
	public static final String NEW_TAG = "new_tag";

	private static final List<LookAtProvider> PROVIDERS = new ArrayList<>();

	static {
		register(simple("health", "Mob health", (t, out) -> {
			if (t.entity() instanceof LivingEntity living) {
				out.add(Component.literal("Health " + Math.round(living.getHealth()) + " / " + Math.round(living.getMaxHealth())).withStyle(ChatFormatting.RED));
			}
		}));
		register(simple("mob_powers", "Elite powers", (t, out) -> {
			dev.vestiges.mobs.MobData d = dev.vestiges.client.mobs.ClientMobs.data(t.entity());
			if (d != null && d.elite()) {
				for (String p : d.powers()) {
					out.add(Component.literal(dev.vestiges.client.mobs.ClientMobs.powerText(p)).withStyle(ChatFormatting.GRAY));
				}
			}
		}));
		register(simple("crop_growth", "Crop growth", (t, out) -> {
			if (t.isBlock()) {
				int pct = cropGrowth(t.state());
				if (pct >= 100) {
					out.add(Component.literal("Ripe").withStyle(ChatFormatting.GREEN));
				} else if (pct >= 0) {
					out.add(Component.literal("Growth " + pct + "%").withStyle(ChatFormatting.YELLOW));
				}
			}
		}));
		register(simple("animal_growth", "Animal growth and breeding", (t, out) -> {
			if (t.entity() instanceof net.minecraft.world.entity.AgeableMob mob && !(mob instanceof net.minecraft.world.entity.npc.villager.AbstractVillager)) {
				com.google.gson.JsonObject info = EntityInfo.get(mob);
				if (info == null || !info.has("age")) {
					return;
				}
				int age = EntityInfo.age(mob);
				if (age < 0) {
					out.add(Component.literal("Grows up in " + time(-age)).withStyle(ChatFormatting.AQUA));
				} else if (mob instanceof net.minecraft.world.entity.animal.Animal) {
					if (info.has("love") && info.get("love").getAsBoolean()) {
						out.add(Component.literal("In love").withStyle(ChatFormatting.LIGHT_PURPLE));
					} else if (age > 0) {
						out.add(Component.literal("Can breed in " + time(age)).withStyle(ChatFormatting.GRAY));
					} else {
						out.add(Component.literal("Ready to breed").withStyle(ChatFormatting.GREEN));
					}
				}
			}
		}));
		register(simple("mount_stats", "Mount stats", (t, out) -> {
			if (t.entity() instanceof net.minecraft.world.entity.animal.equine.AbstractHorse horse) {
				double speed = horse.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) * 42.16;
				String line = String.format(java.util.Locale.ROOT, "Speed %.1f blocks/s", speed);
				if (!(horse instanceof net.minecraft.world.entity.animal.camel.Camel)) {
					double j = horse.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
					double height = -0.1817584952 * j * j * j + 3.689713992 * j * j + 2.128599134 * j - 0.343930367;
					line += String.format(java.util.Locale.ROOT, "  \u00B7  Jump %.1f blocks", Math.max(0, height));
				}
				out.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
				if (horse instanceof net.minecraft.world.entity.animal.equine.Llama llama) {
					out.add(Component.literal("Strength " + llama.getStrength() + " (" + llama.getStrength() * 3 + " chest slots)").withStyle(ChatFormatting.GRAY));
				}
			}
		}));
		register(simple("owner", "Owner of tamed animals", (t, out) -> {
			if (t.entity() instanceof net.minecraft.world.entity.OwnableEntity own && own.getOwnerReference() != null) {
				com.google.gson.JsonObject info = EntityInfo.get(t.entity());
				if (info != null && info.has("owner")) {
					out.add(Component.literal("Owner: " + info.get("owner").getAsString()).withStyle(ChatFormatting.GRAY));
				}
			}
		}));
		register(simple("tool", "Tool hint", (t, out) -> {
			if (t.isBlock() && t.state().requiresCorrectToolForDrops()) {
				out.add(Component.literal("Needs the right tool to drop").withStyle(ChatFormatting.GRAY));
			}
		}));
		register(simple("source", "Which mod it comes from", (t, out) -> {
			// Only for things other mods add; vanilla needs no label.
			String ns = t.id().getNamespace();
			if (!"minecraft".equals(ns)) {
				String name = FabricLoader.getInstance().getModContainer(ns).map(c -> c.getMetadata().getName()).orElse(ns);
				out.add(Component.literal(name).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
			}
		}));
	}

	private LookAtProviders() {
	}

	/** Growth in % for crops and other growing plants, 100 = ripe; -1 when the block does not grow. */
	static int cropGrowth(net.minecraft.world.level.block.state.BlockState s) {
		net.minecraft.world.level.block.Block b = s.getBlock();
		if (b instanceof net.minecraft.world.level.block.CropBlock crop) {
			return crop.getAge(s) * 100 / crop.getMaxAge();
		}
		net.minecraft.world.level.block.state.properties.IntegerProperty age = null;
		int max = 0;
		if (b instanceof net.minecraft.world.level.block.NetherWartBlock) {
			age = net.minecraft.world.level.block.NetherWartBlock.AGE;
			max = net.minecraft.world.level.block.NetherWartBlock.MAX_AGE;
		} else if (b instanceof net.minecraft.world.level.block.CocoaBlock) {
			age = net.minecraft.world.level.block.CocoaBlock.AGE;
			max = net.minecraft.world.level.block.CocoaBlock.MAX_AGE;
		} else if (b instanceof net.minecraft.world.level.block.SweetBerryBushBlock) {
			age = net.minecraft.world.level.block.SweetBerryBushBlock.AGE;
			max = net.minecraft.world.level.block.SweetBerryBushBlock.MAX_AGE;
		} else if (b instanceof net.minecraft.world.level.block.PitcherCropBlock) {
			age = net.minecraft.world.level.block.PitcherCropBlock.AGE;
			max = net.minecraft.world.level.block.PitcherCropBlock.MAX_AGE;
		} else if (b instanceof net.minecraft.world.level.block.StemBlock) {
			age = net.minecraft.world.level.block.StemBlock.AGE;
			max = net.minecraft.world.level.block.StemBlock.MAX_AGE;
		}
		return age == null || !s.hasProperty(age) ? -1 : s.getValue(age) * 100 / Math.max(1, max);
	}

	/** "12 min" or "40 s" from ticks. */
	static String time(int ticks) {
		int seconds = Math.max(1, ticks / 20);
		return seconds >= 60 ? (seconds + 30) / 60 + " min" : seconds + " s";
	}

	public static void register(LookAtProvider provider) {
		PROVIDERS.add(provider);
	}

	public static List<LookAtProvider> all() {
		return Collections.unmodifiableList(PROVIDERS);
	}

	interface Lines {
		void add(LookAtTarget target, List<Component> out);
	}

	static LookAtProvider simple(String id, String label, Lines lines) {
		return new LookAtProvider() {
			@Override
			public String id() {
				return id;
			}

			@Override
			public String label() {
				return label;
			}

			@Override
			public void addLines(LookAtTarget target, List<Component> out) {
				lines.add(target, out);
			}
		};
	}
}
