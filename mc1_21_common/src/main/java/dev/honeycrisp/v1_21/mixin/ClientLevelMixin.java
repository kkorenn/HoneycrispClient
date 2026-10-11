package dev.honeycrisp.v1_21.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Hooks;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** While the time is fixed, the local +1 tick would write fixed+1 into the real clock: skip it (server time packets still land). */
@Mixin(ClientLevel.class)
public class ClientLevelMixin {
	@WrapOperation(method = "tickTime", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel$ClientLevelData;setDayTime(J)V"))
	private void honeycrisp$tickTime(ClientLevel.ClientLevelData data, long time, Operation<Void> original) {
		Hooks h = Honeycrisp.hooks();
		if (h == null || h.fixedDayTime() < 0) original.call(data, time);
	}
}
