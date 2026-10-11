package dev.honeycrisp.v1_21_11.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Hooks;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Fullbright: Double.floatValue #0 in updateLightTexture is darknessEffectScale, #1 gamma. */
@Mixin(LightTexture.class)
public class LightTextureMixin {
	@ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 1))
	private float honeycrisp$gamma(float gamma) {
		Hooks h = Honeycrisp.hooks();
		double g = h == null ? -1 : h.gammaOverride();
		return g < 0 ? gamma : (float) g;
	}
}
