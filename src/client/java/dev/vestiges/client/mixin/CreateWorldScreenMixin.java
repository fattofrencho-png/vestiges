package dev.vestiges.client.mixin;

import dev.vestiges.client.features.WelcomeTab;
import dev.vestiges.config.ConfigStore;
import java.util.Arrays;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the welcome page as a tab beside Game, World and More, and hands its choice to the world being created. */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {
	@ModifyArg(method = "init", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/components/tabs/MenuTabBar$Builder;addTabs([Lnet/minecraft/client/gui/components/tabs/Tab;)Lnet/minecraft/client/gui/components/tabs/MenuTabBar$Builder;"))
	private Tab[] vestiges$addTab(Tab[] tabs) {
		Tab[] more = Arrays.copyOf(tabs, tabs.length + 1);
		more[tabs.length] = new WelcomeTab((CreateWorldScreen) (Object) this);
		return more;
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void vestiges$backToTab(CallbackInfo ci) {
		if (WelcomeTab.takeReopen()) {
			((CreateWorldScreenAccessor) this).vestiges$tabs().selectTab(WelcomeTab.INDEX, false);
		}
	}

	@Inject(method = "onCreate", at = @At("HEAD"))
	private void vestiges$handOver(CallbackInfo ci) {
		ConfigStore.setPendingWorld(WelcomeTab.choiceFor((CreateWorldScreen) (Object) this));
	}
}
