package dev.honeycrisp.v26_2.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Hooks;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Every time-driven visual (sky, light, timelines) reads clocks through getTotalTicks. Keeps the day count. */
@Mixin(ClientClockManager.class)
public class ClientClockManagerMixin {
	@ModifyReturnValue(method = "getTotalTicks", at = @At("RETURN"))
	private long honeycrisp$time(long ticks) {
		Hooks h = Honeycrisp.hooks();
		long t = h == null ? -1 : h.fixedDayTime();
		return t < 0 ? ticks : Math.floorDiv(ticks, 24000L) * 24000L + t;
	}
}
