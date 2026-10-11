package dev.honeycrisp.ui.widgets;

import dev.honeycrisp.module.ColorSetting;
import dev.honeycrisp.platform.Keys;
import dev.honeycrisp.platform.Renderer;
import dev.honeycrisp.platform.Texture;

import java.nio.ByteBuffer;

import static dev.honeycrisp.ui.widgets.Theme.*;

/**
 * Color well (44×24) that opens the simple picker popover (3.16): preset swatches, saturation/brightness square,
 * hue and alpha sliders, hex readout and a Chroma switch. Esc reverts. ponytail: hex is read-only.
 */
public class ColorWell extends Widget {
	private final Ui ui;
	private final ColorSetting s;

	public ColorWell(Ui ui, ColorSetting s) {
		this.ui = ui;
		this.s = s;
	}

	@Override
	public float prefW() {
		return u(44);
	}

	@Override
	public float prefH() {
		return u(24);
	}

	public ColorWell place(float right, float cy) {
		at(right - u(44), cy - u(12), u(44), u(24));
		return this;
	}

	static void checker(Renderer r, float x, float y, float w, float h, float cell) {
		Gfx.fill(r, x, y, w, h, 0xFFFFFFFF);
		r.pushClip(x, y, w, h);
		for (int j = 0; j * cell < h; j++)
			for (int i = j % 2; i * cell < w; i += 2) Gfx.fill(r, x + i * cell, y + j * cell, cell, cell, 0xFFCCCCCC);
		r.popClip();
	}

	@Override
	public void render(Renderer r, float mx, float my) {
		float rad = u(6);
		Gfx.rect(r, x, y, w, h, rad, fill2);
		if (hovered(mx, my)) Gfx.rect(r, x, y, w, h, rad, hoverOverlay());
		float in = u(3);
		int c = s.argb();
		if ((c >>> 24) < 0xFF) {
			r.pushClip(x + in, y + in, w - 2 * in, h - 2 * in);
			checker(r, x + in, y + in, w - 2 * in, h - 2 * in, u(4));
			r.popClip();
		}
		Gfx.rect(r, x + in, y + in, w - 2 * in, h - 2 * in, u(3), c);
		Gfx.stroke(r, x + in, y + in, w - 2 * in, h - 2 * in, u(3), hairline, 0x26000000);
		if (s.chroma()) Gfx.iconAt(r, "sparkles", x + w / 2, y + h / 2, u(12), 0xFFFFFFFF);
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (button != 0) return false;
		ui.openOverlay(new Popover());
		return true;
	}

	static float[] toHsv(int rgb) {
		float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min, h = 0;
		if (d > 0) {
			if (max == r) h = ((g - b) / d) % 6;
			else if (max == g) h = (b - r) / d + 2;
			else h = (r - g) / d + 4;
			h /= 6;
			if (h < 0) h += 1;
		}
		return new float[]{h, max == 0 ? 0 : d / max, max};
	}

	private static Texture texture(Renderer r, int w, int h, int[] argb) {
		ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4);
		for (int c : argb) {
			int a = c >>> 24;
			buf.put((byte) ((c >> 16 & 255) * a / 255)).put((byte) ((c >> 8 & 255) * a / 255)).put((byte) ((c & 255) * a / 255)).put((byte) a);
		}
		buf.flip();
		return r.createTexture(w, h, buf);
	}

	private final class Popover extends Widget {
		private final int original = s.rawArgb();
		private final boolean originalChroma = s.chroma();
		private final Toggle chroma = new Toggle(new Value<Boolean>() {
			public Boolean get() {
				return s.chroma();
			}

			public void set(Boolean v) {
				s.setChroma(v);
			}
		}, true);
		private float hue, sat, val;
		private int drag = -1;
		private Texture sv, hueTex, alphaTex;
		private float svHue = -1;
		private int alphaRgb = -1;
		private Renderer owner;
		private final int[] presets = {red, orange, yellow, green, blue, purple, pink, graphite, 0xFFFFFFFF, 0xFFAEAEB2, 0xFF3A3A3C, 0xFF000000};

		Popover() {
			float[] hsv = toHsv(s.rawArgb());
			hue = hsv[0];
			sat = hsv[1];
			val = hsv[2];
			float pw = u(248), ph = u(12 + 24 + 12 + 24 + 12 + 120 + 14 + 10 + 14 + 10 + 14 + 24 + 12);
			dev.honeycrisp.platform.Platform p = dev.honeycrisp.Honeycrisp.platform();
			float sw = p != null ? p.scaledWidth() : 1e5f, sh = p != null ? p.scaledHeight() : 1e5f;
			float px = Math.max(u(4), Math.min(ColorWell.this.x + ColorWell.this.w - pw, sw - pw - u(4)));
			float py = ColorWell.this.y + ColorWell.this.h + u(6);
			if (py + ph > sh - u(4)) py = Math.max(u(4), ColorWell.this.y - ph - u(6));
			at(px, py, pw, ph);
		}

		private float left() {
			return x + u(12);
		}

		private float swatchTop() {
			return y + u(12);
		}

		private float svTop() {
			return y + u(12 + 24 + 12 + 24 + 12);
		}

		private float hueTop() {
			return svTop() + u(120 + 14);
		}

		private float alphaTop() {
			return hueTop() + u(10 + 14);
		}

		@Override
		public boolean hit(float mx, float my) {
			return true;
		}

		private void apply() {
			int a = s.rawArgb() >>> 24;
			s.set(a << 24 | ColorSetting.hsv(hue, sat, val));
		}

		private void rebuild(Renderer r) {
			if (owner != r) {
				owner = r;
				sv = hueTex = alphaTex = null;
			}
			if (sv == null || svHue != hue) {
				if (sv != null) r.deleteTexture(sv);
				int[] px = new int[32 * 16];
				for (int j = 0; j < 16; j++) for (int i = 0; i < 32; i++) px[j * 32 + i] = 0xFF000000 | ColorSetting.hsv(hue, i / 31f, 1 - j / 15f);
				sv = texture(r, 32, 16, px);
				svHue = hue;
			}
			if (hueTex == null) {
				int[] px = new int[64];
				for (int i = 0; i < 64; i++) px[i] = 0xFF000000 | ColorSetting.hsv(i / 63f, 1, 1);
				hueTex = texture(r, 64, 1, px);
			}
			int rgb = s.rawArgb() & 0xFFFFFF;
			if (alphaTex == null || alphaRgb != rgb) {
				if (alphaTex != null) r.deleteTexture(alphaTex);
				int[] px = new int[32];
				for (int i = 0; i < 32; i++) px[i] = Math.round(i * 255 / 31f) << 24 | rgb;
				alphaTex = texture(r, 32, 1, px);
				alphaRgb = rgb;
			}
		}

		void release() {
			if (owner == null) return;
			if (sv != null) owner.deleteTexture(sv);
			if (hueTex != null) owner.deleteTexture(hueTex);
			if (alphaTex != null) owner.deleteTexture(alphaTex);
			sv = hueTex = alphaTex = null;
		}

		@Override
		public void render(Renderer r, float mx, float my) {
			rebuild(r);
			Gfx.windowGlass(r, x, y, w, h, u(16));
			float lx = left(), iw = u(224);
			// swatches: 2 rows × 6, 24u circles, 12u gaps
			float gap = (iw - 6 * u(24)) / 5;
			for (int i = 0; i < 12; i++) {
				float sx = lx + (i % 6) * (u(24) + gap), sy = swatchTop() + (i / 6) * u(24 + 12);
				Gfx.capsule(r, sx, sy, u(24), u(24), presets[i]);
				Gfx.stroke(r, sx, sy, u(24), u(24), u(12), hairline, 0x33000000);
				if ((presets[i] & 0xFFFFFF) == (s.rawArgb() & 0xFFFFFF) && !s.chroma())
					Gfx.stroke(r, sx - u(4), sy - u(4), u(32), u(32), u(16), u(2), label);
			}
			// saturation / brightness
			float st = svTop();
			r.texture(sv, lx, st, iw, u(120), 0.5f / 32, 0.5f / 16, 31.5f / 32, 15.5f / 16, Gfx.c(0xFFFFFFFF));
			Gfx.stroke(r, lx, st, iw, u(120), u(2), hairline, 0x33000000);
			handle(r, lx + sat * iw, st + (1 - val) * u(120), u(12));
			// hue
			float ht = hueTop();
			r.texture(hueTex, lx, ht, iw, u(10), 0.5f / 64, 0, 63.5f / 64, 1, Gfx.c(0xFFFFFFFF));
			handle(r, lx + hue * iw, ht + u(5), u(14));
			// alpha
			float at = alphaTop();
			checker(r, lx, at, iw, u(10), u(5));
			r.texture(alphaTex, lx, at, iw, u(10), 0.5f / 32, 0, 31.5f / 32, 1, Gfx.c(0xFFFFFFFF));
			handle(r, lx + (s.rawArgb() >>> 24) / 255f * iw, at + u(5), u(14));
			// hex + chroma
			float rowY = at + u(10 + 14), cy = rowY + u(12);
			Gfx.rect(r, lx, rowY, u(100), u(24), u(6), fill3);
			Gfx.textMid(r, String.format("#%08X", s.rawArgb()), lx + u(8), cy, Theme.mono, label);
			chroma.place(x + w - u(12), cy);
			ui.add(chroma).render(r, mx, my);
			Gfx.textMidRight(r, "Chroma", chroma.x - u(8), cy, Theme.body, label);
		}

		private void handle(Renderer r, float cx, float cy, float d) {
			Gfx.shadow(r, cx - d / 2, cy - d / 2 + u(1), d, d, d / 2, u(3), 0x59000000);
			Gfx.stroke(r, cx - d / 2, cy - d / 2, d, d, d / 2, u(2), 0xFFFFFFFF);
		}

		@Override
		public boolean mouseClicked(float mx, float my, int button) {
			if (!(mx >= x && mx < x + w && my >= y && my < y + h)) {
				close();
				return true;
			}
			float lx = left(), iw = u(224);
			if (my >= swatchTop() && my < swatchTop() + u(60)) {
				float gap = (iw - 6 * u(24)) / 5;
				int col = (int) ((mx - lx) / (u(24) + gap)), row = (int) ((my - swatchTop()) / u(36));
				if (col >= 0 && col < 6 && row >= 0 && row < 2) {
					int c = presets[row * 6 + col];
					s.setChroma(false);
					s.set((s.rawArgb() & 0xFF000000) | (c & 0xFFFFFF));
					float[] hsv = toHsv(c);
					hue = hsv[0];
					sat = hsv[1];
					val = hsv[2];
				}
				return true;
			}
			if (my >= svTop() - u(6) && my < svTop() + u(126)) drag = 0;
			else if (my >= hueTop() - u(7) && my < hueTop() + u(17)) drag = 1;
			else if (my >= alphaTop() - u(7) && my < alphaTop() + u(17)) drag = 2;
			else return true;
			mouseDragged(mx, my, button);
			return true;
		}

		@Override
		public void mouseDragged(float mx, float my, int button) {
			float fx = Math.max(0, Math.min(1, (mx - left()) / u(224)));
			if (drag == 0) {
				sat = fx;
				val = Math.max(0, Math.min(1, 1 - (my - svTop()) / u(120)));
				apply();
			} else if (drag == 1) {
				hue = Math.min(fx, 0.999f);
				apply();
			} else if (drag == 2) {
				s.set(Math.round(fx * 255) << 24 | (s.rawArgb() & 0xFFFFFF));
			}
		}

		@Override
		public void mouseReleased(float mx, float my, int button) {
			drag = -1;
		}

		@Override
		public boolean keyPressed(int key, int mods) {
			if (key == Keys.ESCAPE) {
				s.set(original);
				s.setChroma(originalChroma);
				close();
				return true;
			}
			if (key == Keys.ENTER) {
				close();
				return true;
			}
			return false;
		}

		private void close() {
			release();
			ui.closeOverlay();
		}
	}
}
