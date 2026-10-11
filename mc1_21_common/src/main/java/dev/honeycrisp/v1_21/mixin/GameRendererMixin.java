package dev.honeycrisp.v1_21.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Hooks;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	/** Zoom scales the world FOV only (useFovSetting = false is the hand's FOV). */
	@ModifyReturnValue(method = "getFov", at = @At("RETURN"))
	private float honeycrisp$zoom(float fov, Camera camera, float partialTick, boolean useFovSetting) {
		Hooks h = Honeycrisp.hooks();
		return h == null || !useFovSetting ? fov : (float) (fov * h.fovMultiplier(partialTick));
	}

	/** No first-person hand (or screen overlays) while freelook shows the third-person camera. */
	@WrapOperation(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
	private boolean honeycrisp$hand(CameraType type, Operation<Boolean> original) {
		Hooks h = Honeycrisp.hooks();
		return original.call(type) && (h == null || !h.freelookThirdPerson());
	}
}
