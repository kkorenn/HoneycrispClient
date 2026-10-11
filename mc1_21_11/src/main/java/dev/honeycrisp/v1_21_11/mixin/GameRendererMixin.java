package dev.honeycrisp.v1_21_11.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Hooks;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	/** useFovSetting = false is the hand projection (fixed 70°), which zoom leaves alone. */
	@ModifyReturnValue(method = "getFov", at = @At("RETURN"))
	private float honeycrisp$zoom(float fov, Camera camera, float partialTicks, boolean useFovSetting) {
		Hooks h = Honeycrisp.hooks();
		return h == null || !useFovSetting ? fov : (float) (fov * h.fovMultiplier(partialTicks));
	}
}
