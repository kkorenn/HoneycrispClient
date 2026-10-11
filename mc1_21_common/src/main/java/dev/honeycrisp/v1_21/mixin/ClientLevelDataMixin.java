package dev.honeycrisp.v1_21.mixin;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Hooks;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time Changer: every client day-time read (sky, celestial angle, Level.getDayTime) goes through ClientLevelData. */
@Mixin(ClientLevel.ClientLevelData.class)
public class ClientLevelDataMixin {
	@Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
	private void honeycrisp$dayTime(CallbackInfoReturnable<Long> cir) {
		Hooks h = Honeycrisp.hooks();
		long t = h == null ? -1 : h.fixedDayTime();
		if (t >= 0) cir.setReturnValue(t);
	}
}
