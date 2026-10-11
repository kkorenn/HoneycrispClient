package dev.honeycrisp.ui.draw;

import dev.honeycrisp.mac.MacNative;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class PrimitivesTest {
	static final float SCALE = 2;
	static final int W = 600, H = 400;

	@Test
	void nativeBridge() {
		assertTrue(MacNative.available());
		assertEquals(0xFF, MacNative.accentColor() >>> 24);
		double s = MacNative.backingScale();
		assertTrue(s >= 1 && s <= 4, "backingScale " + s);
		assertNull(MacNative.renderSymbol("no.such.symbol.xyz", 32, 24, 400));
		assertNotNull(MacNative.renderSymbol("gearshape", 32, 24, 400));
		int[] info = new int[4];
		assertNull(MacNative.renderText("   ", 13, 400, false, info));
		byte[] px = MacNative.renderText("Hello", 13, 400, false, info);
		assertEquals(info[0] * info[1] * 4, px.length);
		float[] m = MacNative.measureText("Hello", 13, 400, false);
		assertTrue(m[0] > 20 && m[0] < 40 && m[1] > 10, "metrics " + m[0] + " " + m[1]);
		assertEquals(2 * MacNative.measureText("Hello", 13, 400, false)[0], MacNative.measureText("HelloHello", 13, 400, false)[0], 0.5);
	}

	@Test
	void primitivesSheet() throws Exception {
		Java2DRenderer r = new Java2DRenderer((int) (W * SCALE), (int) (H * SCALE), SCALE);
		new FakePlatform(r, W, H, SCALE).install();
		Draw.releaseAll(r);
		background(r);
		boolean dark = Draw.darkAppearance();
		int accent = Draw.accent();

		// left: every font preset over a sidebar
		r.blurSupported = true;
		Draw.glass(r, 8, 8, 200, H - 16, 10, GlassStyle.SIDEBAR);
		Object[][] presets = {{"LARGE_TITLE", Font.LARGE_TITLE}, {"TITLE", Font.TITLE}, {"TITLE2", Font.TITLE2}, {"TITLE3", Font.TITLE3},
			{"HEADLINE", Font.HEADLINE}, {"BODY", Font.BODY}, {"CALLOUT", Font.CALLOUT}, {"SUBHEADLINE", Font.SUBHEADLINE},
			{"FOOTNOTE", Font.FOOTNOTE}, {"CAPTION", Font.CAPTION}, {"CAPTION2", Font.CAPTION2}, {"HUD", Font.HUD}, {"HUD_BOLD", Font.HUD_BOLD}};
		float y = 14;
		for (Object[] p : presets) {
			Font f = (Font) p[1];
			Draw.text(r, (String) p[0], 16, y, f, 0xFFFFFFFF);
			Draw.text(r, f.size + "", 190 - Draw.textWidth(f.size + "", Font.CAPTION2), y, Font.CAPTION2, 0x99FFFFFF);
			y += Draw.lineHeight(f) + 2;
		}
		Draw.text(r, "Grüße — naïve café · ±½ → ✓ 日本語 Ελληνικά Привет", 16, y + 2, Font.BODY, 0xFFFFFFFF);
		Draw.text(r, "fps 144 · x 128.5 y 64 z -2048", 16, y + 14, Font.mono(5.5f, 500), 0xFFFFD60A);
		Draw.textCentered(r, "centered", 108, y + 26, Font.CALLOUT, 0xBFFFFFFF);
		Draw.text(r, "dark=" + dark + " accent=#" + Integer.toHexString(accent), 16, y + 38, Font.FOOTNOTE, accent);
		// baseline/box check: line box outline behind text
		float tw = Draw.textWidth("Box", Font.TITLE3);
		r.fill(16, H - 40, tw, Draw.lineHeight(Font.TITLE3), 0x40FF3B30);
		Draw.text(r, "Box", 16, H - 40, Font.TITLE3, 0xFFFFFFFF);

		// right: icons
		String[] icons = {"speedometer", "keyboard", "gearshape", "eye", "gamecontroller", "person.crop.circle", "cursorarrow.rays", "no.such.symbol"};
		float x = 220;
		for (String s : icons) {
			Draw.roundRect(r, x, 12, 24, 24, 6, 0x33FFFFFF);
			Draw.icon(r, s, x + 4, 16, 16, 0xFFFFFFFF);
			x += 30;
		}
		x = 220;
		for (int i = 0; i < 6; i++) {
			Draw.icon(r, icons[i], x, 42, 8, accent);
			x += 12;
		}

		// shapes: fills, strokes, pills, small radii
		Draw.roundRect(r, 220, 60, 60, 40, 10, accent);
		Draw.roundRect(r, 290, 60, 60, 40, 20, 0xFF34C759);
		Draw.roundRect(r, 360, 60, 100, 20, 10, 0xFFFF9F0A);
		Draw.roundRect(r, 360, 84, 30, 16, 3, 0xFFFF375F);
		Draw.roundRect(r, 395, 84, 16, 16, 8, 0xFFFFFFFF);
		Draw.roundRect(r, 470, 60, 120, 40, 0, 0x80FFFFFF);
		Draw.roundRectStroke(r, 220, 108, 60, 40, 10, 0.5f, 0xFFFFFFFF);
		Draw.roundRectStroke(r, 290, 108, 60, 40, 12, 2, accent);
		Draw.roundRectStroke(r, 360, 108, 100, 20, 10, 1, 0xFFFFFFFF);
		Draw.roundRect(r, 470, 108, 44, 26, 13, 0xFF34C759);
		Draw.roundRect(r, 470 + 20, 110, 22, 22, 11, 0xFFFFFFFF);
		Draw.roundRect(r, 520, 108, 44, 26, 13, 0x40FFFFFF);
		Draw.roundRect(r, 522, 110, 22, 22, 11, 0xFFFFFFFF);

		// shadows
		for (int i = 0; i < 3; i++) {
			float sx = 230 + i * 80, b = 4 + i * 8;
			Draw.shadow(r, sx, 168 + i * 2, 60, 40, 10, b, 0x99000000);
			Draw.roundRect(r, sx, 166, 60, 40, 10, 0xFF2C2C2E);
			Draw.text(r, "blur " + (int) b, sx + 6, 172, Font.CAPTION, 0xFFFFFFFF);
		}

		// glass panels: blurred vs fallback
		r.blurSupported = true;
		Draw.glass(r, 220, 226, 170, 80, 12, GlassStyle.WINDOW);
		Draw.text(r, "WINDOW (blur)", 230, 234, Font.HEADLINE, 0xFFFFFFFF);
		Draw.icon(r, "speedometer", 230, 250, 10, accent);
		Draw.text(r, "Honeycrisp Client", 244, 251, Font.BODY, 0xD9FFFFFF);
		r.blurSupported = false;
		Draw.glass(r, 400, 226, 170, 80, 12, GlassStyle.WINDOW);
		Draw.text(r, "WINDOW (fallback)", 410, 234, Font.HEADLINE, 0xFFFFFFFF);
		Draw.glass(r, 410, 260, 70, 16, 5, GlassStyle.CONTROL);
		Draw.textCentered(r, "Button", 445, 263, Font.CALLOUT, 0xFFFFFFFF);
		r.blurSupported = true;
		Draw.glass(r, 220, 320, 80, 22, 6, GlassStyle.HUD);
		Draw.text(r, "144 FPS", 228, 326, Font.HUD_BOLD, 0xFFFFFFFF);
		Draw.glass(r, 310, 320, 80, 22, 6, GlassStyle.HUD);
		Draw.text(r, "CPS 12 | 9", 318, 326, Font.HUD, 0xFFFFFFFF);
		Draw.glass(r, 400, 320, 80, 22, 11, GlassStyle.CONTROL.withTint(accent & 0x00FFFFFF | 0xCC000000));
		Draw.textCentered(r, "Accent", 440, 326, Font.HEADLINE, 0xFFFFFFFF);

		// clip
		r.pushClip(500, 330, 40, 30);
		Draw.roundRect(r, 490, 320, 60, 50, 14, 0xFFBF5AF2);
		r.popClip();

		File out = new File("build/test-output/primitives.png");
		out.getParentFile().mkdirs();
		ImageIO.write(r.canvas, "png", out);
		// 3x zoom of the shapes/icons area for inspection
		java.awt.image.BufferedImage zoom = new java.awt.image.BufferedImage(600, 450, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D zg = zoom.createGraphics();
		zg.drawImage(r.canvas, 0, 0, 600, 450, (int) (218 * SCALE), (int) (10 * SCALE), (int) (318 * SCALE), (int) (85 * SCALE), null);
		ImageIO.write(zoom, "png", new File("build/test-output/primitives-zoom.png"));

		int live = r.liveTextures;
		Draw.releaseAll(r);
		assertEquals(0, r.liveTextures, "releaseAll frees " + live + " textures");
	}

	static void background(Java2DRenderer r) {
		r.g.setPaint(new Color(0x1B1530));
		r.g.fill(new Rectangle2D.Float(0, 0, r.canvas.getWidth(), r.canvas.getHeight()));
		int[][] blobs = {{300, 250, 0xFF2D55}, {900, 500, 0x0A84FF}, {700, 150, 0x30D158}, {1100, 700, 0xFF9F0A}, {100, 700, 0xBF5AF2}};
		for (int[] b : blobs) {
			r.g.setPaint(new RadialGradientPaint(new Point2D.Float(b[0], b[1]), 320, new float[]{0, 1},
				new Color[]{new Color(b[2] | 0xB0000000, true), new Color(b[2], true)}));
			r.g.fill(new Rectangle2D.Float(0, 0, r.canvas.getWidth(), r.canvas.getHeight()));
		}
		for (int i = 0; i < 12; i++) {
			r.g.setColor(new Color(255, 255, 255, 40));
			r.g.fillRect(440 + i * 60, 440, 20, 200);
		}
	}
}
