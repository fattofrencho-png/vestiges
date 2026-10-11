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
