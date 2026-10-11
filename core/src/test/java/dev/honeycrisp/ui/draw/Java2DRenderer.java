package dev.honeycrisp.ui.draw;

import dev.honeycrisp.platform.Renderer;
import dev.honeycrisp.platform.Texture;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.DataBufferInt;
import java.awt.image.Kernel;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

/** Headless Renderer onto a premultiplied BufferedImage, in framebuffer pixels (GUI coords × scale). */
public final class Java2DRenderer implements Renderer {
	public final BufferedImage canvas;
	public final Graphics2D g;
	private final float scale;
	private final Deque<Shape> clips = new ArrayDeque<>();
	public boolean blurSupported;
	public int liveTextures;

	public Java2DRenderer(int width, int height, float scale) {
		this.scale = scale;
		canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
		g = canvas.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
	}

	static final class Tex implements Texture {
		final BufferedImage img;

		Tex(BufferedImage img) {
			this.img = img;
		}

		public int width() {
			return img.getWidth();
		}

		public int height() {
			return img.getHeight();
		}
	}

	@Override
	public Texture createTexture(int width, int height, ByteBuffer rgba) {
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
		int[] data = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
		for (int i = 0; i < width * height; i++) {
			int r = rgba.get(i * 4) & 255, gr = rgba.get(i * 4 + 1) & 255, b = rgba.get(i * 4 + 2) & 255, a = rgba.get(i * 4 + 3) & 255;
			if (r > a || gr > a || b > a) throw new IllegalStateException("texture not premultiplied at " + i);
			data[i] = a << 24 | r << 16 | gr << 8 | b;
		}
		liveTextures++;
		return new Tex(img);
	}

	@Override
	public void deleteTexture(Texture t) {
		liveTextures--;
	}

	@Override
	public void fill(float x, float y, float w, float h, int argb) {
		g.setColor(new Color(argb, true));
		g.fill(new Rectangle2D.Float(x * scale, y * scale, w * scale, h * scale));
	}

	/** Tint is non-premultiplied ARGB: premultiply it, then multiply every premultiplied texel by it. */
	@Override
	public void texture(Texture texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int tint) {
		BufferedImage src = ((Tex) texture).img;
		int sx0 = Math.round(u0 * src.getWidth()), sy0 = Math.round(v0 * src.getHeight());
		int sx1 = Math.round(u1 * src.getWidth()), sy1 = Math.round(v1 * src.getHeight());
		if (sx1 <= sx0 || sy1 <= sy0) return;
		int ta = tint >>> 24, tr = (tint >> 16 & 255) * ta / 255, tg = (tint >> 8 & 255) * ta / 255, tb = (tint & 255) * ta / 255;
		BufferedImage part = new BufferedImage(sx1 - sx0, sy1 - sy0, BufferedImage.TYPE_INT_ARGB_PRE);
		int[] out = ((DataBufferInt) part.getRaster().getDataBuffer()).getData();
		int[] in = ((DataBufferInt) src.getRaster().getDataBuffer()).getData();
		for (int yy = sy0; yy < sy1; yy++)
			for (int xx = sx0; xx < sx1; xx++) {
				int p = in[yy * src.getWidth() + xx];
				out[(yy - sy0) * part.getWidth() + xx - sx0] = ((p >>> 24) * ta / 255) << 24 | ((p >> 16 & 255) * tr / 255) << 16
					| ((p >> 8 & 255) * tg / 255) << 8 | (p & 255) * tb / 255;
			}
		int dx0 = Math.round(x * scale), dy0 = Math.round(y * scale), dx1 = Math.round((x + w) * scale), dy1 = Math.round((y + h) * scale);
		g.drawImage(part, dx0, dy0, dx1, dy1, 0, 0, part.getWidth(), part.getHeight(), null);
	}

	@Override
	public void pushClip(float x, float y, float w, float h) {
		clips.push(g.getClip() == null ? new Rectangle2D.Float(0, 0, canvas.getWidth(), canvas.getHeight()) : g.getClip());
		g.clip(new Rectangle2D.Float(x * scale, y * scale, w * scale, h * scale));
	}

	@Override
	public void popClip() {
		g.setClip(clips.pop());
	}

	@Override
	public boolean blur(float x, float y, float w, float h, float radius) {
		if (!blurSupported) return false;
		int r = Math.max(1, Math.round(radius * scale)), pad = r;
		int x0 = Math.max(0, Math.round(x * scale) - pad), y0 = Math.max(0, Math.round(y * scale) - pad);
		int x1 = Math.min(canvas.getWidth(), Math.round((x + w) * scale) + pad), y1 = Math.min(canvas.getHeight(), Math.round((y + h) * scale) + pad);
		BufferedImage region = new BufferedImage(x1 - x0, y1 - y0, BufferedImage.TYPE_INT_ARGB_PRE);
		region.getGraphics().drawImage(canvas.getSubimage(x0, y0, x1 - x0, y1 - y0), 0, 0, null);
		float sigma = r / 2f;
		float[] k = new float[2 * r + 1];
		float sum = 0;
		for (int i = -r; i <= r; i++) sum += k[i + r] = (float) Math.exp(-i * i / (2 * sigma * sigma));
		for (int i = 0; i < k.length; i++) k[i] /= sum;
		region = new ConvolveOp(new Kernel(k.length, 1, k), ConvolveOp.EDGE_NO_OP, null).filter(region, null);
		region = new ConvolveOp(new Kernel(1, k.length, k), ConvolveOp.EDGE_NO_OP, null).filter(region, null);
		Shape old = g.getClip();
		g.clip(new Rectangle2D.Float(x * scale, y * scale, w * scale, h * scale));
		g.setComposite(AlphaComposite.Src);
		g.drawImage(region, x0, y0, null);
		g.setComposite(AlphaComposite.SrcOver);
		g.setClip(old);
		return true;
	}
}
