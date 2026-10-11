package dev.honeycrisp.v1_21.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccess {
	/** Private blit with float UVs and a raw texture view (no TextureManager id needed). */
	@Invoker("submitBlit")
	void honeycrisp$submitBlit(RenderPipeline pipeline, GpuTextureView textureView, int x0, int y0, int x1, int y1,
		float u0, float u1, float v0, float v1, int color);
}
