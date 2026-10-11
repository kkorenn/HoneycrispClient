package dev.honeycrisp.v26_3.mixin;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	/** setScreen(null) without a level falls back to a new TitleScreen, so that case is replaced too. */
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void honeycrisp$replaceTitle(Screen screen, CallbackInfo ci) {
		Hooks h = Honeycrisp.hooks();
		if (h == null || !(screen instanceof TitleScreen || screen == null && minecraft.level == null)) return;
		if (h.replaceMainMenu()) {
			ci.cancel();
			h.openMainMenu();
		}
	}
}
