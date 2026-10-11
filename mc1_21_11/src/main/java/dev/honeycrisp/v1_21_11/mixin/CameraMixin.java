package dev.honeycrisp.v1_21_11.mixin;

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
	@WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
	private float honeycrisp$freelookYaw(Entity entity, float a, Operation<Float> original) {
		Hooks h = Honeycrisp.hooks();
		return h != null && h.freelookActive() ? h.freelookYaw() : original.call(entity, a);
	}

	@WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
	private float honeycrisp$freelookPitch(Entity entity, float a, Operation<Float> original) {
		Hooks h = Honeycrisp.hooks();
		return h != null && h.freelookActive() ? h.freelookPitch() : original.call(entity, a);
	}
}
