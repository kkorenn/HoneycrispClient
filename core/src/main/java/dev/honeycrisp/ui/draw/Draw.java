package dev.honeycrisp.ui.draw;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.mac.MacNative;
import dev.honeycrisp.platform.Platform;
import dev.honeycrisp.platform.Renderer;
import dev.honeycrisp.platform.Texture;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Core drawing primitives in GUI-scaled px. Text, SF Symbols and shapes are rasterized by libhoneycrisp at
 * pixel scale = {@link Platform#guiScale()}, cached as textures (LRU keyed by content, style and scale) and drawn at
 * pixel-snapped positions. Textures are white; the color comes from the tint. Render thread only.
 */
public final class Draw {
	/** Continuous corners reach about 1.6× the radius along each edge (corner smoothing 0.6). */
	private static final float CORNER_EXTENT = 1.6f;
	/** SF Symbol point size relative to the icon box. */
	private static final float ICON_POINT = 0.8f;
	private static final int MAX_TEXTURES = 768;

	private static final class Tex {
		final Texture texture;
		final int w, h, ox, oy;

		Tex(Texture texture, int w, int h, int ox, int oy) {
			this.texture = texture;
			this.w = w;
			this.h = h;
			this.ox = ox;
			this.oy = oy;
		}
	}

	private static final Tex NONE = new Tex(null, 0, 0, 0, 0);
	private static Renderer owner;
	private static final LinkedHashMap<String, Tex> textures = new LinkedHashMap<String, Tex>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Tex> e) {
			if (size() <= MAX_TEXTURES) return false;
			if (e.getValue().texture != null) owner.deleteTexture(e.getValue().texture);
			return true;
		}
	};
	private static final LinkedHashMap<String, float[]> measures = new LinkedHashMap<String, float[]>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, float[]> e) {
			return size() > 4096;
		}
	};
	private static int accent = 0xFF007AFF;
	private static boolean dark = true;
	private static long systemPolled = Long.MIN_VALUE;

	private Draw() {
	}

	// ---- text

	/** One line of text; (x, y) is the top-left of the line box ({@link #lineHeight} tall). */
	public static void text(Renderer r, String s, float x, float y, Font f, int argb) {
		if (s == null || s.isEmpty() || (argb >>> 24) == 0 || !MacNative.available()) return;
		float k = scale();
		Tex t = cached(r, "t|" + f.key + "|" + k + "|" + s, s, f, k);
		if (t.texture == null) return;
		int px = Math.round(x * k) - t.ox, py = Math.round((y + metrics(f, k)[1]) * k) - t.oy;
		r.texture(t.texture, px / k, py / k, t.w / k, t.h / k, 0, 0, 1, 1, argb);
	}

	public static void textCentered(Renderer r, String s, float cx, float y, Font f, int argb) {
		text(r, s, cx - textWidth(s, f) / 2, y, f, argb);
	}

	public static float textWidth(String s, Font f) {
		if (s == null || s.isEmpty()) return 0;
		float k = scale();
		if (!MacNative.available()) return s.length() * f.size * 0.55f;
		String key = "w|" + f.key + "|" + k + "|" + s;
		float[] m = measures.get(key);
		if (m == null) measures.put(key, m = MacNative.measureText(s, f.size * k, f.weight, f.mono));
		return m[0] / k;
	}

	/** Ascent + descent + leading, snapped to framebuffer pixels. */
	public static float lineHeight(Font f) {
		float k = scale();
		if (!MacNative.available()) return f.size * 1.2f;
		float[] m = metrics(f, k);
		return (float) Math.ceil((m[1] + m[2] + m[3]) * k) / k;
	}

	/** {width, ascent, descent, leading} in GUI px for an empty line of this font. */
	private static float[] metrics(Font f, float k) {
		String key = "m|" + f.key + "|" + k;
		float[] m = measures.get(key);
		if (m == null) {
			m = MacNative.measureText("", f.size * k, f.weight, f.mono);
			for (int i = 0; i < 4; i++) m[i] /= k;
			measures.put(key, m);
		}
		return m;
	}

	private static Tex cached(Renderer r, String key, String s, Font f, float k) {
		Tex t = lookup(r, key);
		if (t != null) return t;
		int[] info = new int[4];
		byte[] px = MacNative.renderText(s, f.size * k, f.weight, f.mono, info);
		return store(r, key, px, info[0], info[1], info[2], info[3]);
	}

	// ---- icons

	/** SF Symbol (by name, e.g. "gearshape") centered in a size×size box. Unknown names draw nothing. */
	public static void icon(Renderer r, String sfSymbol, float x, float y, float size, int argb) {
		if ((argb >>> 24) == 0 || !MacNative.available()) return;
		float k = scale();
		int box = Math.round(size * k);
		if (box <= 0) return;
		String key = "i|" + sfSymbol + "|" + box;
		Tex t = lookup(r, key);
		if (t == null) t = store(r, key, MacNative.renderSymbol(sfSymbol, box, box * ICON_POINT, 400), box, box, 0, 0);
		if (t.texture == null) return;
		r.texture(t.texture, Math.round(x * k) / k, Math.round(y * k) / k, box / k, box / k, 0, 0, 1, 1, argb);
	}

	// ---- shapes

	/** Filled rect with continuous (squircle) corners. */
	public static void roundRect(Renderer r, float x, float y, float w, float h, float radius, int argb) {
		shape(r, x, y, w, h, radius, 0, 0, argb);
	}

	/** Stroke inside the rect's edge. */
	public static void roundRectStroke(Renderer r, float x, float y, float w, float h, float radius, float lineWidth, int argb) {
		if (lineWidth > 0) shape(r, x, y, w, h, radius, lineWidth, 0, argb);
	}

	/** Gaussian shadow of the rounded rect (blur ≈ CSS blur radius, sigma = blur / 2). Offset it yourself. */
	public static void shadow(Renderer r, float x, float y, float w, float h, float radius, float blur, int argb) {
		shape(r, x, y, w, h, radius, 0, Math.max(0, blur), argb);
	}

	private static void shape(Renderer r, float x, float y, float w, float h, float radius, float stroke, float blur, int argb) {
		if ((argb >>> 24) == 0 || !MacNative.available()) return;
		float k = scale();
		int x0 = Math.round(x * k), y0 = Math.round(y * k);
		int wpx = Math.round((x + w) * k) - x0, hpx = Math.round((y + h) * k) - y0;
		if (wpx <= 0 || hpx <= 0) return;
		float rpx = Math.max(0, radius * k), spx = stroke * k, sigma = blur * k / 2;
		int m = sigma > 0 ? (int) Math.ceil(sigma * 3) + 1 : 0;
		int e = (int) Math.ceil(Math.min(rpx * CORNER_EXTENT, Math.min(wpx, hpx) / 2f));
		int cap = 2 * (e + m) + 2;
		int sw = Math.min(wpx, cap), sh = Math.min(hpx, cap);
		String key = "s|" + sw + "|" + sh + "|" + rpx + "|" + spx + "|" + sigma;
		Tex t = lookup(r, key);
		if (t == null) t = store(r, key, MacNative.renderRoundRect(sw, sh, rpx, spx, sigma, m), sw + 2 * m, sh + 2 * m, 0, 0);
		if (t.texture != null) nineSlice(r, t, x0 - m, y0 - m, wpx + 2 * m, hpx + 2 * m, k, argb);
	}

	/** Stretches the middle two texels of t (built symmetric: corner, 2 px, corner) to dw×dh px at (dx, dy) px. */
	private static void nineSlice(Renderer r, Tex t, int dx, int dy, int dw, int dh, float k, int argb) {
		int[] sc = slices(t.w, dw), dc = destSlices(sc, dw), sr = slices(t.h, dh), dr = destSlices(sr, dh);
		for (int i = 0; i < 3; i++) {
			if (dc[i + 1] <= dc[i]) continue;
			for (int j = 0; j < 3; j++) {
				if (dr[j + 1] <= dr[j]) continue;
				r.texture(t.texture, (dx + dc[i]) / k, (dy + dr[j]) / k, (dc[i + 1] - dc[i]) / k, (dr[j + 1] - dr[j]) / k,
					sc[i] / (float) t.w, sr[j] / (float) t.h, sc[i + 1] / (float) t.w, sr[j + 1] / (float) t.h, argb);
			}
		}
	}

	private static int[] slices(int size, int dest) {
		if (size >= dest) return new int[]{0, 0, size, size};
		int a = (size - 2) / 2;
		return new int[]{0, a, a + 2, size};
	}

	private static int[] destSlices(int[] s, int dest) {
		return new int[]{0, s[1], dest - (s[3] - s[2]), dest};
	}

	// ---- glass

	/**
	 * Material panel: shadow, backdrop blur when the renderer supports it (tint) or a more opaque fallback tint,
	 * then a hairline highlight edge. The backdrop blur is rectangular; the tint carries the rounded shape.
	 */
	public static void glass(Renderer r, float x, float y, float w, float h, float radius, GlassStyle style) {
		if (style.shadowBlur > 0) shadow(r, x, y + style.shadowOffsetY, w, h, radius, style.shadowBlur, style.shadowColor);
		boolean blurred = style.blurRadius > 0 && r.blur(x, y, w, h, style.blurRadius);
		roundRect(r, x, y, w, h, radius, blurred ? style.tint : style.fallbackTint);
		roundRectStroke(r, x, y, w, h, radius, 1 / scale(), style.highlight);
	}

	// ---- system

	/** macOS accent color (System Settings → Appearance), opaque ARGB. */
	public static int accent() {
		pollSystem();
		return accent;
	}

	public static boolean darkAppearance() {
		pollSystem();
		return dark;
	}

	private static void pollSystem() {
		long now = System.nanoTime();
		if (now - systemPolled < 1_000_000_000L && systemPolled != Long.MIN_VALUE) return;
		systemPolled = now;
		if (!MacNative.available()) return;
		accent = MacNative.accentColor();
		dark = MacNative.darkAppearance();
	}

	// ---- cache

	/** Deletes every cached texture (resource reload, renderer change). */
	public static void releaseAll(Renderer r) {
		for (Tex t : textures.values()) if (t.texture != null) (owner != null ? owner : r).deleteTexture(t.texture);
		textures.clear();
		measures.clear();
		owner = r;
	}

	private static Tex lookup(Renderer r, String key) {
		if (owner != r) releaseAll(r);
		return textures.get(key);
	}

	private static Tex store(Renderer r, String key, byte[] px, int w, int h, int ox, int oy) {
		Tex t = NONE;
		if (px != null && w > 0 && h > 0) {
			ByteBuffer buf = ByteBuffer.allocateDirect(px.length);
			buf.put(px);
			buf.flip();
			t = new Tex(r.createTexture(w, h, buf), w, h, ox, oy);
		}
		textures.put(key, t);
		return t;
	}

	private static float scale() {
		Platform p = Honeycrisp.platform();
		double s = p != null ? p.guiScale() : 2;
		return s > 0 ? (float) s : 1;
	}
}
