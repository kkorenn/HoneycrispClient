package dev.honeycrisp.v1_21.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Hooks;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/** Fullbright: the first OptionInstance.get() after options.gamma() is the gamma value. */
@Mixin(LightTexture.class)
public class LightTextureMixin {
	@ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0),
		slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;gamma()Lnet/minecraft/client/OptionInstance;")))
	private Object honeycrisp$gamma(Object gamma) {
		Hooks h = Honeycrisp.hooks();
		double g = h == null ? -1 : h.gammaOverride();
		return g < 0 ? gamma : Double.valueOf(g);
	}
}
