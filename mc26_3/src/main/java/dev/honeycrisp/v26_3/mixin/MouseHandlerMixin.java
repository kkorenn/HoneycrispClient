package dev.honeycrisp.v26_3.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Hooks;
import dev.honeycrisp.v26_3.McPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.ScrollWheelHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "onButton", at = @At("HEAD"))
	private void honeycrisp$button(long handle, MouseButtonInfo rawButtonInfo, int action, CallbackInfo ci) {
		Hooks h = Honeycrisp.hooks();
		if (h != null && handle == minecraft.getWindow().handle()) h.onMouseButton(McPlatform.toGlfwButton(rawButtonInfo.button()), action,
			minecraft.gui.screen() != null);
	}

	/** Zoom sensitivity scales the final look delta; freelook takes it instead of the player. */
	@WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void honeycrisp$turn(LocalPlayer player, double dx, double dy, Operation<Void> original) {
		Hooks h = Honeycrisp.hooks();
		if (h != null) {
			double m = h.mouseSensitivityMultiplier();
			dx *= m;
			dy *= m;
			if (h.consumeMouseLook(dx, dy)) return;
		}
		original.call(player, dx, dy);
	}

	/** Only reached in-world with a player (no screen); a zero result makes onScroll return before the hotbar change. */
	@WrapOperation(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ScrollWheelHandler;onMouseScroll(DD)Lorg/joml/Vector2i;"))
	private Vector2i honeycrisp$scroll(ScrollWheelHandler handler, double x, double y, Operation<Vector2i> original) {
		Hooks h = Honeycrisp.hooks();
		return h != null && y != 0 && h.onScroll(y) ? new Vector2i() : original.call(handler, x, y);
	}
}
