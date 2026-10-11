package dev.honeycrisp.v26_3.mixin;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Hooks;
import dev.honeycrisp.v26_3.McPlatform;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void honeycrisp$key(long handle, int action, KeyEvent event, CallbackInfo ci) {
		Hooks h = Honeycrisp.hooks();
		if (h == null || handle != minecraft.getWindow().handle()) return;
		int key = McPlatform.toGlfwKey(event.key());
		if (key != -1 && h.onKey(key, McPlatform.toGlfwAction(action), minecraft.gui.screen() != null)) ci.cancel();
	}
}
