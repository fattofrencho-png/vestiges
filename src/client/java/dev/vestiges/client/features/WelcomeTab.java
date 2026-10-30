package dev.vestiges.client.features;

import com.mojang.datafixers.util.Pair;
import dev.vestiges.client.mixin.CreateWorldScreenAccessor;
import dev.vestiges.config.ConfigStore;
import dev.vestiges.config.ServerConfig;
import dev.vestiges.config.VestigesSystem;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.WorldDataConfiguration;

/** The Vestiges tab of the Create World screen. The choice lives as long as the screen, because a data pack change rebuilds the tabs. */
public final class WelcomeTab extends GridLayoutTab {
	public static final int INDEX = 3;
	private static final String ENCHANT_PACK_SUFFIX = "enchant_levels";
	private static WeakReference<CreateWorldScreen> owner = new WeakReference<>(null);
	private static ServerConfig choice;
	private static boolean reopen;
	private final FeaturePanel panel;

	public WelcomeTab(CreateWorldScreen screen) {
		super(Component.literal("Vestiges"));
		panel = new FeaturePanel(choiceFor(screen), true, () -> syncEnchantPack(screen));
		layout.addChild(panel, 0, 0);
	}

	public static ServerConfig choiceFor(CreateWorldScreen screen) {
		if (owner.get() != screen || choice == null) {
			owner = new WeakReference<>(screen);
			choice = ConfigStore.defaults();
		}
		return choice;
	}

	/** True once after the enchant pack changed: the rebuilt screen should come back to this tab. */
	public static boolean takeReopen() {
		boolean r = reopen;
		reopen = false;
		return r;
	}

	@Override
	public void doLayout(ScreenRectangle rectangle) {
		panel.setRectangle(rectangle.width() - 16, rectangle.height() - 8, rectangle.left() + 8, rectangle.top() + 4);
		super.doLayout(rectangle);
	}

	/** The enchant levels are a data pack, and the world's registries are built from its packs: the screen has to reload them to change it. */
	private static void syncEnchantPack(CreateWorldScreen screen) {
		CreateWorldScreenAccessor access = (CreateWorldScreenAccessor) screen;
		WorldDataConfiguration data = screen.getUiState().getSettings().dataConfiguration();
		Pair<Path, PackRepository> settings = access.vestiges$packSettings(data);
		if (settings == null) {
			return;
		}
		PackRepository repo = settings.getSecond();
		boolean wanted = choice.enabled(VestigesSystem.ENCHANT_LEVELS);
		List<String> selected = new ArrayList<>(repo.getSelectedIds());
		boolean has = selected.stream().anyMatch(id -> id.endsWith(ENCHANT_PACK_SUFFIX));
		if (has == wanted) {
			return;
		}
		if (wanted) {
			repo.getAvailableIds().stream().filter(id -> id.endsWith(ENCHANT_PACK_SUFFIX)).forEach(selected::add);
		} else {
			selected.removeIf(id -> id.endsWith(ENCHANT_PACK_SUFFIX));
		}
		repo.setSelected(selected);
		reopen = true;
		access.vestiges$applyPacks(repo, true, config -> {
		});
	}
}
