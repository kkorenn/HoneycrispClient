package dev.honeycrisp.v26_3.mixin;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Hooks;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Toggle sprint/sneak: OR into the shift (5) and sprint (6) args of the Input record built each tick. */
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
	private static final String INPUT = "Lnet/minecraft/world/entity/player/Input;<init>(ZZZZZZZ)V";

	@ModifyArg(method = "tick", at = @At(value = "INVOKE", target = INPUT), index = 5)
	private boolean honeycrisp$sneak(boolean down) {
		Hooks h = Honeycrisp.hooks();
		return down || h != null && h.sneakHeld();
	}

	@ModifyArg(method = "tick", at = @At(value = "INVOKE", target = INPUT), index = 6)
	private boolean honeycrisp$sprint(boolean down) {
		Hooks h = Honeycrisp.hooks();
		return down || h != null && h.sprintHeld();
	}
}
