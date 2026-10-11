package dev.honeycrisp.v26_2.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Hooks;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public class CameraMixin {
	@ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
	private float honeycrisp$zoom(float fov, float partialTicks) {
		Hooks h = Honeycrisp.hooks();
		return h == null ? fov : (float) (fov * h.fovMultiplier(partialTicks));
	}

	@WrapOperation(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
	private float honeycrisp$freelookYaw(Entity entity, float a, Operation<Float> original) {
		Hooks h = Honeycrisp.hooks();
		return h != null && h.freelookActive() ? h.freelookYaw() : original.call(entity, a);
	}

	@WrapOperation(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
	private float honeycrisp$freelookPitch(Entity entity, float a, Operation<Float> original) {
		Hooks h = Honeycrisp.hooks();
		return h != null && h.freelookActive() ? h.freelookPitch() : original.call(entity, a);
	}
}
