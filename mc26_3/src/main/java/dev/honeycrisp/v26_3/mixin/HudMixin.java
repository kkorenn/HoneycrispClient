package dev.honeycrisp.v26_3.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Crosshair;
import dev.honeycrisp.hooks.Hooks;
import dev.honeycrisp.v26_3.McRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {
	@Shadow
	public abstract boolean isHidden();

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void honeycrisp$hud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		Hooks h = Honeycrisp.hooks();
		if (h == null || isHidden()) return;
		graphics.nextStratum();
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
		McRenderer.draw(graphics, () -> h.onRenderHud(partialTick));
	}

	/** The first 6-arg blitSprite in extractCrosshair is the crosshair sprite (attack indicator draws follow it). */
	@WrapOperation(method = "extractCrosshair", at = @At(value = "INVOKE", ordinal = 0,
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private void honeycrisp$crosshair(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h,
		Operation<Void> original) {
		Hooks hooks = Honeycrisp.hooks();
		Crosshair c = hooks == null ? null : hooks.crosshair();
		if (c == null) {
			original.call(graphics, pipeline, sprite, x, y, w, h);
			return;
		}
		McRenderer.draw(graphics, () -> c.draw(McRenderer.INSTANCE, graphics.guiWidth() / 2f, graphics.guiHeight() / 2f));
	}

	@ModifyExpressionValue(method = "displayScoreboardSidebar", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/scores/Objective;numberFormatOrDefault(Lnet/minecraft/network/chat/numbers/NumberFormat;)Lnet/minecraft/network/chat/numbers/NumberFormat;"))
	private NumberFormat honeycrisp$scoreNumbers(NumberFormat format) {
		Hooks h = Honeycrisp.hooks();
		return h != null && h.hideScoreboardNumbers() ? BlankFormat.INSTANCE : format;
	}
}
