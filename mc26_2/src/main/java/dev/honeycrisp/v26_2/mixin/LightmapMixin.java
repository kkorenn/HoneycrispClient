package dev.honeycrisp.v26_2.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Hooks;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Fullbright: the first Double.floatValue in extract is options.gamma().get(). */
@Mixin(LightmapRenderStateExtractor.class)
public class LightmapMixin {
	@ModifyExpressionValue(method = "extract", at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 0))
	private float honeycrisp$gamma(float gamma) {
		Hooks h = Honeycrisp.hooks();
		double g = h == null ? -1 : h.gammaOverride();
		return g < 0 ? gamma : (float) g;
	}
}
