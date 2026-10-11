package dev.honeycrisp.v1_21;

import com.mojang.blaze3d.platform.NativeImage;
import dev.honeycrisp.platform.Renderer;
import dev.honeycrisp.platform.Texture;
import dev.honeycrisp.v1_21.mixin.GuiGraphicsAccess;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Draws into the GUI frame being built (1.21.6+ defers GUI drawing through GuiRenderState). Float rects go through
 * the pose matrix (unit quad scaled), so nothing snaps to whole GUI pixels except clip rects.
 */
public final class McRenderer implements Renderer {
	public static final McRenderer INSTANCE = new McRenderer();
	/** Screen.render draws the background itself (1.21.5 only; 1.21.6+ calls renderBackground before render). */
	static final boolean RENDER_DRAWS_BACKGROUND = false;
	private static GuiGraphics graphics;

	private McRenderer() {
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

	static void item(ItemStack stack, float x, float y) {
		GuiGraphics g = graphics;
		if (g == null) return;
		g.pose().pushMatrix().translate(x, y);
		g.renderItem(stack, 0, 0);
		g.pose().popMatrix();
	}

	static void effect(Holder<MobEffect> effect, float x, float y) {
		GuiGraphics g = graphics;
		if (g == null) return;
		g.pose().pushMatrix().translate(x, y);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(effect), 0, 0, 18, 18);
		g.pose().popMatrix();
	}

	record Tex(DynamicTexture texture, int width, int height) implements Texture {
	}

	@Override
	public Texture createTexture(int width, int height, ByteBuffer premultipliedRgba) {
		NativeImage image = new NativeImage(width, height, false);
		MemoryUtil.memCopy(MemoryUtil.memAddress(premultipliedRgba), image.getPointer(), (long) width * height * 4);
		DynamicTexture t = new DynamicTexture(() -> "honeycrisp", image);
		t.setFilter(true, false);
		return new Tex(t, width, height);
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
		// Premultiplied pipeline: the vertex color multiplies rgb and alpha, so premultiply the tint too.
		int a = argbTint >>> 24;
		int tint = a << 24 | ((argbTint >> 16 & 255) * a / 255) << 16 | ((argbTint >> 8 & 255) * a / 255) << 8 | (argbTint & 255) * a / 255;
		g.pose().pushMatrix().translate(x, y).scale(w, h);
		((GuiGraphicsAccess) g).honeycrisp$submitBlit(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, ((Tex) texture).texture.getTextureView(),
			0, 0, 1, 1, u0, u1, v0, v1, tint);
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

	// ponytail: GuiRenderState only blurs whole strata once per frame; no rect blur on 1.21.6+.
	@Override
	public boolean blur(float x, float y, float w, float h, float radius) {
		return false;
	}
}
