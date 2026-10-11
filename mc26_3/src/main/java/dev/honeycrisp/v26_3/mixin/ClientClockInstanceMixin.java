package dev.honeycrisp.v26_3.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Hooks;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Time Changer: timelines (sky, light) sample totalTicks(); the clock's own field (ticking, server updates) is untouched. */
@Mixin(ClientClockManager.ClientClockInstance.class)
public class ClientClockInstanceMixin {
	@ModifyReturnValue(method = "totalTicks", at = @At("RETURN"))
	private long honeycrisp$time(long ticks) {
		Hooks h = Honeycrisp.hooks();
		long t = h == null ? -1 : h.fixedDayTime();
		return t < 0 ? ticks : Math.floorDiv(ticks, 24000L) * 24000L + t;
	}
}
