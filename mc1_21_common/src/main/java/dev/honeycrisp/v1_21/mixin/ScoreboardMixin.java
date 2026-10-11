package dev.honeycrisp.v1_21.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Hooks;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public class ScoreboardMixin {
	// ponytail: per-score number formats set by the server (numberFormatOverride) still show; hook the sidebar lambda if that matters.
	@WrapOperation(method = "displayScoreboardSidebar", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/scores/Objective;numberFormatOrDefault(Lnet/minecraft/network/chat/numbers/NumberFormat;)Lnet/minecraft/network/chat/numbers/NumberFormat;"))
	private NumberFormat honeycrisp$hideNumbers(Objective objective, NumberFormat fallback, Operation<NumberFormat> original) {
		Hooks h = Honeycrisp.hooks();
		return h != null && h.hideScoreboardNumbers() ? BlankFormat.INSTANCE : original.call(objective, fallback);
	}
}
