package dev.honeycrisp.v1_21_11;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.honeycrisp.platform.Renderer;
import dev.honeycrisp.platform.Texture;
import dev.honeycrisp.v1_21_11.mixin.GuiGraphicsInvoker;
import dev.honeycrisp.v1_21_11.mixin.OverlayTextureAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Draws into the GUI frame being built (1.21.6+ defers GUI drawing through GuiRenderState). Float rects go through
 * the pose matrix (unit quad scaled), so nothing snaps to whole GUI pixels except clip rects.
 */
public final class McRenderer implements Renderer {
	public static final McRenderer INSTANCE = new McRenderer();
	private static final int VANILLA_HURT = 0xB2FF0000;
	private static GuiGraphics graphics;
	private static int appliedHitColor = VANILLA_HURT;

	private McRenderer() {
	}

	public static GuiGraphics graphics() {
		return graphics;
	}

	/** Runs core drawing with {@code g} as the target (HUD, screens, crosshair). */
	public static void draw(GuiGraphics g, Runnable r) {
		GuiGraphics prev = graphics;
		graphics = g;
		try {
			r.run();
		} finally {
			graphics = prev;
		}
	}

	static final class Tex implements Texture {
		final DynamicTexture texture;
		final int width, height;

		Tex(DynamicTexture texture, int width, int height) {
			this.texture = texture;
			this.width = width;
			this.height = height;
		}

		@Override
		public int width() {
			return width;
		}

		@Override
		public int height() {
			return height;
		}
	}

	@Override
	public Texture createTexture(int width, int height, ByteBuffer premultipliedRgba) {
		NativeImage image = new NativeImage(width, height, false);
		MemoryUtil.memByteBuffer(image.getPointer(), width * height * 4).put(premultipliedRgba.duplicate());
		return new Tex(new DynamicTexture(() -> "honeycrisp", image), width, height);
	}

	@Override
	public void deleteTexture(Texture texture) {
		((Tex) texture).texture.close();
	}

	@Override
	public void fill(float x, float y, float w, float h, int argb) {
		GuiGraphics g = graphics;
		if (g == null || w <= 0 || h <= 0) return;
		g.pose().pushMatrix().translate(x, y).scale(w, h);
		g.fill(0, 0, 1, 1, argb);
		g.pose().popMatrix();
	}

	@Override
	public void texture(Texture texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int argbTint) {
		GuiGraphics g = graphics;
		if (g == null || w <= 0 || h <= 0) return;
		DynamicTexture t = ((Tex) texture).texture;
		// Premultiplied pipeline: the vertex color multiplies rgb and alpha, so premultiply the tint too.
		int a = argbTint >>> 24;
		int tint = a << 24 | ((argbTint >> 16 & 255) * a / 255) << 16 | ((argbTint >> 8 & 255) * a / 255) << 8 | (argbTint & 255) * a / 255;
		g.pose().pushMatrix().translate(x, y).scale(w, h);
		((GuiGraphicsInvoker) g).honeycrisp$submitBlit(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, t.getTextureView(),
			RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), 0, 0, 1, 1, u0, u1, v0, v1, tint);
		g.pose().popMatrix();
	}

	@Override
	public void pushClip(float x, float y, float w, float h) {
		if (graphics != null) graphics.enableScissor((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + w), (int) Math.ceil(y + h));
	}

	@Override
	public void popClip() {
		if (graphics != null) graphics.disableScissor();
	}

	// ponytail: GuiRenderState only blurs whole strata once per frame (blurBeforeThisStratum); no rect blur on 1.21.11.
	@Override
	public boolean blur(float x, float y, float w, float h, float radius) {
		return false;
	}

	/** Recolors the entity hurt overlay (OverlayTexture rows 0–7); 0 restores vanilla red. Called once per tick. */
	public static void applyHitColor(int argb) {
		int color = argb == 0 ? VANILLA_HURT : argb;
		if (color == appliedHitColor) return;
		appliedHitColor = color;
		DynamicTexture t = ((OverlayTextureAccessor) Minecraft.getInstance().gameRenderer.overlayTexture()).honeycrisp$texture();
		NativeImage pixels = t.getPixels();
		for (int y = 0; y < 8; y++)
			for (int x = 0; x < 16; x++)
				pixels.setPixel(x, y, color);
		t.upload();
	}
}
