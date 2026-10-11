package dev.honeycrisp.v1_7_10;

import dev.honeycrisp.platform.Renderer;
import dev.honeycrisp.platform.Texture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

/** Tessellator + plain GL11 (1.7.10 has no GlStateManager); Graft turns these GL calls into Metal. */
final class Render1710 implements Renderer {
	private final ArrayDeque<int[]> clips = new ArrayDeque<int[]>();

	static final class Tex implements Texture {
		final int id, width, height;

		Tex(int id, int width, int height) {
			this.id = id;
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
	public Texture createTexture(int width, int height, ByteBuffer rgba) {
		if (!rgba.isDirect()) {
			ByteBuffer copy = BufferUtils.createByteBuffer(rgba.remaining());
			copy.put(rgba.duplicate()).flip();
			rgba = copy;
		}
		int id = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
		return new Tex(id, width, height);
	}

	@Override
	public void deleteTexture(Texture texture) {
		GL11.glDeleteTextures(((Tex)texture).id);
	}

	private static void begin() {
		GL11.glEnable(GL11.GL_BLEND);
		OpenGlHelper.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glColor4f(1, 1, 1, 1);
	}

	private static void end() {
		OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
	}

	@Override
	public void fill(float x, float y, float w, float h, int argb) {
		begin();
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		Tessellator t = Tessellator.instance;
		t.startDrawingQuads();
		color(t, argb);
		t.addVertex(x, y + h, 0);
		t.addVertex(x + w, y + h, 0);
		t.addVertex(x + w, y, 0);
		t.addVertex(x, y, 0);
		t.draw();
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		end();
	}

	@Override
	public void texture(Texture texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int tint) {
		begin();
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, ((Tex)texture).id);
		Tessellator t = Tessellator.instance;
		t.startDrawingQuads();
		color(t, tint);
		t.addVertexWithUV(x, y + h, 0, u0, v1);
		t.addVertexWithUV(x + w, y + h, 0, u1, v1);
		t.addVertexWithUV(x + w, y, 0, u1, v0);
		t.addVertexWithUV(x, y, 0, u0, v0);
		t.draw();
		end();
	}

	/** Premultiplied. */
	private static void color(Tessellator t, int argb) {
		int a = argb >>> 24;
		t.setColorRGBA((argb >> 16 & 255) * a / 255, (argb >> 8 & 255) * a / 255, (argb & 255) * a / 255, a);
	}

	@Override
	public void pushClip(float x, float y, float w, float h) {
		Minecraft mc = Minecraft.getMinecraft();
		int s = Platform1710.scaled().getScaleFactor();
		int x0 = (int)Math.floor(x * s), x1 = (int)Math.ceil((x + w) * s);
		int y0 = mc.displayHeight - (int)Math.ceil((y + h) * s), y1 = mc.displayHeight - (int)Math.floor(y * s);
		int[] top = clips.peek();
		if (top != null) {
			x0 = Math.max(x0, top[0]);
			y0 = Math.max(y0, top[1]);
			x1 = Math.min(x1, top[2]);
			y1 = Math.min(y1, top[3]);
		}
		int[] r = {x0, y0, Math.max(x0, x1), Math.max(y0, y1)};
		clips.push(r);
		scissor(r);
	}

	@Override
	public void popClip() {
		clips.pop();
		int[] top = clips.peek();
		if (top == null) {
			GL11.glDisable(GL11.GL_SCISSOR_TEST);
		} else {
			scissor(top);
		}
	}

	private static void scissor(int[] r) {
		GL11.glEnable(GL11.GL_SCISSOR_TEST);
		GL11.glScissor(r[0], r[1], r[2] - r[0], r[3] - r[1]);
	}

	@Override
	public boolean blur(float x, float y, float w, float h, float radius) {
		return false;
	}
}
