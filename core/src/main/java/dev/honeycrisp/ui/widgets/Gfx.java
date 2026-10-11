package dev.honeycrisp.ui.widgets;

import dev.honeycrisp.platform.Renderer;
import dev.honeycrisp.ui.draw.Draw;
import dev.honeycrisp.ui.draw.Font;

import java.util.ArrayList;
import java.util.List;

import static dev.honeycrisp.ui.widgets.Theme.*;

/**
 * {@link Draw} with a global opacity (fades, disabled controls) plus the DESIGN.md material recipes and text helpers.
 * All UI code draws through here.
 */
public final class Gfx {
	private Gfx() {
	}

	/** Multiplies the alpha of everything drawn; set it around a block and restore it. */
	public static float opacity = 1;

	public static int c(int argb) {
		return opacity >= 1 ? argb : Theme.alpha(argb, opacity);
	}

	public static void fill(Renderer r, float x, float y, float w, float h, int argb) {
		int a = c(argb);
		if ((a >>> 24) != 0) r.fill(x, y, w, h, a);
	}

	public static void rect(Renderer r, float x, float y, float w, float h, float radius, int argb) {
		Draw.roundRect(r, x, y, w, h, Math.min(radius, Math.min(w, h) / 2), c(argb));
	}

	public static void capsule(Renderer r, float x, float y, float w, float h, int argb) {
		rect(r, x, y, w, h, h / 2, argb);
	}

	public static void stroke(Renderer r, float x, float y, float w, float h, float radius, float width, int argb) {
		Draw.roundRectStroke(r, x, y, w, h, Math.min(radius, Math.min(w, h) / 2), width, c(argb));
	}

	public static void shadow(Renderer r, float x, float y, float w, float h, float radius, float blur, int argb) {
		Draw.shadow(r, x, y, w, h, Math.min(radius, Math.min(w, h) / 2), blur, c(argb));
	}

	public static void icon(Renderer r, String name, float x, float y, float size, int argb) {
		Draw.icon(r, name, x, y, size, c(argb));
	}

	/** Icon centered on (cx, cy). */
	public static void iconAt(Renderer r, String name, float cx, float cy, float size, int argb) {
		Draw.icon(r, name, cx - size / 2, cy - size / 2, size, c(argb));
	}

	public static void text(Renderer r, String s, float x, float y, Font f, int argb) {
		Draw.text(r, s, x, y, f, c(argb));
	}

	/** Text whose line box is vertically centered on cy. */
	public static void textMid(Renderer r, String s, float x, float cy, Font f, int argb) {
		Draw.text(r, s, x, cy - Draw.lineHeight(f) / 2, f, c(argb));
	}

	public static void textMidCentered(Renderer r, String s, float cx, float cy, Font f, int argb) {
		textMid(r, s, cx - width(s, f) / 2, cy, f, argb);
	}

	public static void textMidRight(Renderer r, String s, float right, float cy, Font f, int argb) {
		textMid(r, s, right - width(s, f), cy, f, argb);
	}

	public static float width(String s, Font f) {
		return Draw.textWidth(s, f);
	}

	public static float lineHeight(Font f) {
		return Draw.lineHeight(f);
	}

	/** Tail-truncates with "…" to fit maxW. */
	public static String truncate(String s, Font f, float maxW) {
		if (s == null || width(s, f) <= maxW) return s;
		int lo = 0, hi = s.length();
		while (lo < hi) {
			int mid = (lo + hi + 1) / 2;
			if (width(s.substring(0, mid).trim() + "…", f) <= maxW) lo = mid;
			else hi = mid - 1;
		}
		return s.substring(0, lo).trim() + "…";
	}

	/** Word-wraps to at most maxLines, truncating the last. */
	public static List<String> wrap(String s, Font f, float maxW, int maxLines) {
		List<String> out = new ArrayList<String>();
		if (s == null || s.isEmpty()) return out;
		String[] words = s.split(" ");
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < words.length; i++) {
			String next = line.length() == 0 ? words[i] : line + " " + words[i];
			if (width(next, f) <= maxW || line.length() == 0) {
				line.setLength(0);
				line.append(next);
				continue;
			}
			if (out.size() == maxLines - 1) {
				StringBuilder rest = new StringBuilder(line);
				for (int j = i; j < words.length; j++) rest.append(' ').append(words[j]);
				out.add(truncate(rest.toString(), f, maxW));
				return out;
			}
			out.add(line.toString());
			line.setLength(0);
			line.append(words[i]);
		}
		out.add(truncate(line.toString(), f, maxW));
		return out;
	}

	// ---- materials (2.4)

	/** Backdrop blur when enabled and supported; false = use the no-blur tint. */
	private static boolean backdrop(Renderer r, float x, float y, float w, float h, float blurU) {
		return blur && !reduceTransparency && opacity > 0.5f && r.blur(x, y, w, h, u(blurU));
	}

	private static void edges(Renderer r, float x, float y, float w, float h, float radius, int highlight, int specular) {
		stroke(r, x, y, w, h, radius, u(1), highlight);
		if (reduceTransparency || (specular >>> 24) == 0) return;
		r.pushClip(x, y, w, h * 0.33f);
		stroke(r, x, y, w, h, radius, u(1), specular);
		r.popClip();
	}

	/** L1 Window: mod menu window, sheets, menus, popovers, toolbars. */
	public static void windowGlass(Renderer r, float x, float y, float w, float h, float radius) {
		shadow(r, x, y + u(16), w, h, radius, u(48), dark ? 0x66000000 : 0x2E000000);
		shadow(r, x, y + u(1), w, h, radius, u(3), dark ? 0x40000000 : 0x1A000000);
		boolean b = backdrop(r, x, y, w, h, 24);
		int tint = reduceTransparency ? (dark ? 0xF51C1C1E : 0xF5F6F6F8) : b ? (dark ? 0xB31C1C1E : 0xB8F6F6F8) : (dark ? 0xEB1C1C1E : 0xF2F6F6F8);
		rect(r, x, y, w, h, radius, tint);
		float hl = hairline;
		stroke(r, x - hl, y - hl, w + 2 * hl, h + 2 * hl, radius + hl, hl, dark ? 0x80000000 : 0x1F000000);
		edges(r, x, y, w, h, radius, strokeHi, strokeSpec);
	}

	/** L2 group: sidebar, form groups, cards (card = with its small shadow). */
	public static void groupGlass(Renderer r, float x, float y, float w, float h, float radius, boolean card) {
		if (card) shadow(r, x, y + u(2), w, h, radius, u(8), dark ? 0x33000000 : 0x14000000);
		rect(r, x, y, w, h, radius, reduceTransparency ? (dark ? 0xFF2C2C2E : 0xFFFFFFFF) : dark ? 0x14FFFFFF : 0x99FFFFFF);
		edges(r, x, y, w, h, radius, strokeHi, Theme.alpha(strokeSpec, 0.5f));
	}

	/** L3 control: button bodies, knobs, thumbs, search capsule. */
	public static void controlGlass(Renderer r, float x, float y, float w, float h, float radius) {
		shadow(r, x, y + u(1), w, h, radius, u(4), dark ? 0x40000000 : 0x26000000);
		rect(r, x, y, w, h, radius, reduceTransparency ? (dark ? 0xFF3A3A3C : 0xFFFFFFFF) : dark ? 0x2EFFFFFF : 0xD9FFFFFF);
		edges(r, x, y, w, h, radius, dark ? 0x33FFFFFF : 0xFFFFFFFF, 0x59FFFFFF);
	}

	/** Clear glass: main-menu buttons over the panorama. */
	public static void clearGlass(Renderer r, float x, float y, float w, float h, float radius) {
		shadow(r, x, y + u(8), w, h, radius, u(24), 0x40000000);
		boolean b = backdrop(r, x, y, w, h, 12);
		rect(r, x, y, w, h, radius, reduceTransparency ? 0xF5202124 : b ? 0x2EFFFFFF : 0x59101114);
		edges(r, x, y, w, h, radius, 0x26FFFFFF, 0x4DFFFFFF);
	}

	/** Keyboard focus ring (3u, 1u outside the shape). */
	public static void focusRing(Renderer r, float x, float y, float w, float h, float radius) {
		float o = u(4);
		stroke(r, x - o, y - o, w + 2 * o, h + 2 * o, radius + o, u(3), accentRing);
	}

	/** White switch knob / slider thumb with its small shadow. */
	public static void knob(Renderer r, float x, float y, float w, float h, boolean glass) {
		if (glass) {
			controlGlass(r, x, y, w, h, h / 2);
			return;
		}
		shadow(r, x, y + u(1), w, h, h / 2, u(3), 0x40000000);
		capsule(r, x, y, w, h, 0xFFFFFFFF);
		if (!dark) stroke(r, x, y, w, h, h / 2, hairline, 0x14000000);
	}

	/** Stepped-alpha fade (no gradient primitive): from argb at the top to transparent over h. */
	public static void fadeDown(Renderer r, float x, float y, float w, float h, int argb) {
		int steps = Math.max(1, Math.round(h / u(2)));
		float sh = h / steps;
		for (int i = 0; i < steps; i++) fill(r, x, y + i * sh, w, sh, Theme.alpha(argb, 1 - (i + 0.5f) / steps));
	}
}
