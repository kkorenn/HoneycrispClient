package dev.honeycrisp.v1_21.mixin;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseButtonMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "onPress", at = @At("HEAD"))
	private void honeycrisp$button(long handle, int button, int action, int mods, CallbackInfo ci) {
		Hooks h = Honeycrisp.hooks();
		if (h != null && handle == minecraft.getWindow().getWindow()) h.onMouseButton(button, action, minecraft.screen != null);
	}
}
