package dev.honeycrisp.v1_21_11.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiGraphics.class)
public interface GuiGraphicsInvoker {
	/** Private blit with float UVs and a raw texture view (no TextureManager id needed). Args: x0, y0, x1, y1, u0, u1, v0, v1. */
	@Invoker("submitBlit")
	void honeycrisp$submitBlit(RenderPipeline pipeline, GpuTextureView textureView, GpuSampler sampler, int x0, int y0, int x1, int y1,
		float u0, float u1, float v0, float v1, int color);
}
