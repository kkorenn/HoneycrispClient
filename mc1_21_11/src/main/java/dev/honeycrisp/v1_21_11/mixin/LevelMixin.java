package dev.honeycrisp.v1_21_11.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Hooks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Timelines (sky, light, moon) read Level.getDayTime; ClientLevel's own tick reads its level data directly. Keeps the day count. */
@Mixin(Level.class)
public class LevelMixin {
	@ModifyReturnValue(method = "getDayTime", at = @At("RETURN"))
	private long honeycrisp$time(long ticks) {
		if (!((Object) this instanceof ClientLevel)) return ticks;
		Hooks h = Honeycrisp.hooks();
		long t = h == null ? -1 : h.fixedDayTime();
		return t < 0 ? ticks : Math.floorDiv(ticks, 24000L) * 24000L + t;
	}
}
