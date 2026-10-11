package dev.honeycrisp.v1_21.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccess {
	@Accessor("bufferSource")
	MultiBufferSource.BufferSource honeycrisp$bufferSource();
}
