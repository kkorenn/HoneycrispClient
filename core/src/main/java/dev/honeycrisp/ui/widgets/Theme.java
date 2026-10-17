package dev.honeycrisp.ui.widgets;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.platform.Platform;
import dev.honeycrisp.ui.draw.Draw;
import dev.honeycrisp.ui.draw.Font;
import dev.honeycrisp.ui.screens.UiPrefs;

/**
 * DESIGN.md tokens for the current frame: colors (dark/light), the resolved accent, the UI unit k (GUI px per u)
 * and the type ramp at that scale. Screens call {@link #frame()} at the start of every render.
 */
public final class Theme {
	private Theme() {
	}

	public static boolean dark = true, reduceTransparency, reduceMotion, blur = true;
	/** GUI px per DESIGN unit: backingScale / guiScale × UI scale. */
	public static float k = 1;
	/** One framebuffer pixel in GUI px. */
	public static float hairline = 0.5f;
	public static long now;

	public static int bgBase, scrim, scrimMenu, label, label2, label3, label4, fill1, fill2, fill3, fill4, separator,
		strokeHi, strokeSpec, red, orange, yellow, green, blue, purple, pink, graphite;
	public static int accent, accentTint, accentRing, onAccent;

	public static Font largeTitle, title, title2, title3, headline, body, bodyBold, callout, calloutMedium, subheadline,
		footnote, caption, caption2, wordmark, mono;
	private static float fontsAt = -1;

	public static void frame() {
		Platform p = Honeycrisp.platform();
		now = p != null ? p.timeMillis() : System.currentTimeMillis();
		UiPrefs prefs = UiPrefs.get();
		double gui = p != null && p.guiScale() > 0 ? p.guiScale() : 2;
		double backing = p != null && p.backingScale() > 0 ? p.backingScale() : 2;
		k = (float) (backing / gui * prefs.uiScale.value());
		hairline = (float) (1 / gui);
		dark = prefs.theme.get() == UiPrefs.Appearance.SYSTEM ? Draw.darkAppearance() : prefs.theme.get() == UiPrefs.Appearance.DARK;
		reduceTransparency = prefs.reduceTransparency.on();
		reduceMotion = prefs.reduceMotion.on();
		blur = prefs.blur.on();
		if (dark) {
			bgBase = 0xFF0E0F12; scrim = 0x59000000; scrimMenu = 0x40000000;
			label = 0xF2FFFFFF; label2 = 0x99EBEBF5; label3 = 0x4DEBEBF5; label4 = 0x29EBEBF5;
			fill1 = 0x5C787880; fill2 = 0x52787880; fill3 = 0x3D767680; fill4 = 0x2E747480;
			separator = reduceTransparency ? 0xFF38383A : 0x33FFFFFF; strokeHi = 0x26FFFFFF; strokeSpec = 0x4DFFFFFF;
			red = 0xFFFF453A; orange = 0xFFFF9F0A; yellow = 0xFFFFD60A; green = 0xFF32D74B; blue = 0xFF0A84FF;
			purple = 0xFFBF5AF2; pink = 0xFFFF375F; graphite = 0xFF8C8C8C;
		} else {
			bgBase = 0xFFF2F2F7; scrim = 0x33000000; scrimMenu = 0x26000000;
			label = 0xF2000000; label2 = 0x993C3C43; label3 = 0x4D3C3C43; label4 = 0x2E3C3C43;
			fill1 = 0x33787880; fill2 = 0x29787880; fill3 = 0x1F767680; fill4 = 0x14747480;
			separator = reduceTransparency ? 0xFFC6C6C8 : 0x1F000000; strokeHi = 0x99FFFFFF; strokeSpec = 0xE6FFFFFF;
			red = 0xFFFF3B30; orange = 0xFFFF9500; yellow = 0xFFFFCC00; green = 0xFF28CD41; blue = 0xFF007AFF;
			purple = 0xFFAF52DE; pink = 0xFFFF2D55; graphite = 0xFF8C8C8C;
		}
		accent = 0xFF000000 | resolveAccent(prefs.accent.get());
		accentTint = alpha(accent, 0.22f);
		accentRing = alpha(accent, 0.5f);
		onAccent = luminance(accent) > 0.6 ? 0xE6000000 : 0xFFFFFFFF;
		if (fontsAt != k) fonts();
	}

	public static int red(boolean dark) {
		return dark ? 0xFFFF453A : 0xFFFF3B30;
	}

	public static int orange(boolean dark) {
		return dark ? 0xFFFF9F0A : 0xFFFF9500;
	}

	public static int green(boolean dark) {
		return dark ? 0xFF32D74B : 0xFF28CD41;
	}

	public static int resolveAccent(UiPrefs.Accent a) {
		switch (a) {
			case BLUE: return blue;
			case PURPLE: return purple;
			case PINK: return pink;
			case RED: return red;
			case ORANGE: return orange;
			case YELLOW: return yellow;
			case GREEN: return green;
			case GRAPHITE: return graphite;
			default: return Draw.accent();
		}
	}

	private static void fonts() {
		fontsAt = k;
		largeTitle = font(26, 700);
		title = font(22, 700);
		title2 = font(17, 600);
		title3 = font(15, 600);
		headline = font(13, 700);
		body = font(13, 400);
		bodyBold = font(13, 600);
		callout = font(12, 400);
		calloutMedium = font(12, 500);
		subheadline = font(11, 400);
		footnote = font(10, 400);
		caption = font(10, 400);
		caption2 = font(10, 500);
		wordmark = font(48, 800);
		mono = Font.mono(12 * k, 500);
	}

	/** SF Pro at a DESIGN size (u) and weight. */
	public static Font font(float sizeU, int weight) {
		return Font.of(sizeU * k, weight);
	}

	/** u → GUI px. */
	public static float u(float v) {
		return v * k;
	}

	public static int alpha(int argb, float a) {
		return Math.round((argb >>> 24) * Math.max(0, Math.min(1, a))) << 24 | (argb & 0xFFFFFF);
	}

	public static int mix(int a, int b, float t) {
		if (t <= 0) return a;
		if (t >= 1) return b;
		int r = 0;
		for (int s = 0; s < 32; s += 8) {
			int ca = (a >>> s) & 0xFF, cb = (b >>> s) & 0xFF;
			r |= Math.round(ca + (cb - ca) * t) << s;
		}
		return r;
	}

	public static double luminance(int argb) {
		return (0.299 * (argb >> 16 & 255) + 0.587 * (argb >> 8 & 255) + 0.114 * (argb & 255)) / 255;
	}

	/** Hover / pressed overlays (2.9). */
	public static int hoverOverlay() {
		return dark ? 0x0FFFFFFF : 0x0A000000;
	}

	public static int pressedOverlay() {
		return dark ? 0x1FFFFFFF : 0x14000000;
	}
}
