package dev.honeycrisp.v1_21.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Crosshair;
import dev.honeycrisp.hooks.Hooks;
import dev.honeycrisp.v1_21.McRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class HudMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "render", at = @At("TAIL"))
	private void honeycrisp$hud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		Hooks h = Honeycrisp.hooks();
		if (h == null || minecraft.options.hideGui) return;
		graphics.nextStratum();
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
		McRenderer.draw(graphics, () -> h.onRenderHud(partialTick));
	}

	/** The first 6-arg blitSprite in renderCrosshair is the crosshair sprite (attack indicator draws follow it). */
	@WrapOperation(method = "renderCrosshair", at = @At(value = "INVOKE", ordinal = 0,
		target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/ResourceLocation;IIII)V"))
	private void honeycrisp$crosshair(GuiGraphics graphics, RenderPipeline pipeline, ResourceLocation sprite, int x, int y, int w, int h,
		Operation<Void> original) {
		Hooks hooks = Honeycrisp.hooks();
		Crosshair c = hooks == null ? null : hooks.crosshair();
		if (c == null) {
			original.call(graphics, pipeline, sprite, x, y, w, h);
			return;
		}
		McRenderer.draw(graphics, () -> c.draw(McRenderer.INSTANCE, graphics.guiWidth() / 2f, graphics.guiHeight() / 2f));
	}
}
