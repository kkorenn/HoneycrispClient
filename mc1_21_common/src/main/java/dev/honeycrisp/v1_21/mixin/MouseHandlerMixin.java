package dev.honeycrisp.v1_21.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Hooks;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.ScrollWheelHandler;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
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

	/** In-world wheel (no screen): a swallowed scroll yields (0, 0), which onScroll treats as nothing. */
	@WrapOperation(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ScrollWheelHandler;onMouseScroll(DD)Lorg/joml/Vector2i;"))
	private Vector2i honeycrisp$scroll(ScrollWheelHandler handler, double x, double y, Operation<Vector2i> original) {
		Hooks h = Honeycrisp.hooks();
		return h != null && h.onScroll(y) ? new Vector2i() : original.call(handler, x, y);
	}
}
