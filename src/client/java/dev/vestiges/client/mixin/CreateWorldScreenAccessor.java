package dev.vestiges.client.mixin;

import com.mojang.datafixers.util.Pair;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.WorldDataConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreateWorldScreen.class)
public interface CreateWorldScreenAccessor {
	@Accessor("tabNavigationBar")
	MenuTabBar vestiges$tabs();

	@Invoker("getDataPackSelectionSettings")
	Pair<Path, PackRepository> vestiges$packSettings(WorldDataConfiguration data);

	@Invoker("tryApplyNewDataPacks")
	void vestiges$applyPacks(PackRepository repository, boolean fromPackScreen, Consumer<WorldDataConfiguration> onFail);
}
