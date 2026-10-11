package dev.honeycrisp.ui.screens;

import dev.honeycrisp.platform.Renderer;
import dev.honeycrisp.ui.widgets.Button;
import dev.honeycrisp.ui.widgets.Gfx;
import dev.honeycrisp.ui.widgets.Segmented;
import dev.honeycrisp.ui.widgets.Ui;
import dev.honeycrisp.ui.widgets.Value;

import static dev.honeycrisp.ui.widgets.Theme.*;

/**
 * Cosmetics (DESIGN 4.6), placeholder until a cosmetics backend exists: category segmented control, a flat player
 * preview and "Coming soon" tiles.
 */
final class CosmeticsPane {
	private static final String[] TABS = {"Capes", "Hats", "Effects"};
	private static final String[] GLYPHS = {"flag", "crown", "sparkles"};
	private static final int TILES = 8;
	private final Ui ui;
	private final Value<Integer> tab = Value.local(0);
	private final Segmented tabs = new Segmented(tab, TABS);
	private final Button unequip = new Button("Unequip", null, Button.Size.REGULAR, Button.Style.SECONDARY, null);

	CosmeticsPane(Ui ui) {
		this.ui = ui;
		unequip.enabled = false;
	}

	void toolbar(Renderer r, float x, float cy, float mx, float my) {
		tabs.at(x, cy - tabs.prefH() / 2, tabs.prefW(), tabs.prefH());
		ui.add(tabs).render(r, mx, my);
	}

	private float previewW(float w) {
		return Math.max(u(220), w * 0.34f);
	}

	float height(float w) {
		int cols = cols(w);
		int rows = (TILES + cols - 1) / cols;
		return Math.max(u(36) + rows * u(120 + 12), u(300 + 12 + 28) + u(36));
	}

	private int cols(float w) {
		float gw = w - previewW(w) - u(16);
		return Math.max(1, (int) ((gw + u(12)) / u(96 + 12)));
	}

	void render(Renderer r, float x, float y, float w, float mx, float my) {
		// banner
		Gfx.iconAt(r, "info.circle", x + u(8), y + u(10), u(14), label2);
		Gfx.textMid(r, "Cosmetics are coming soon. Equip capes, hats and effects here once they launch.", x + u(22), y + u(10), subheadline, label2);
		y += u(36);
		// preview
		float pw = previewW(w), ph = u(300);
		Gfx.groupGlass(r, x, y, pw, ph, u(16), false);
		player(r, x + pw / 2, y + ph / 2 - u(6), u(7));
		Gfx.textMidCentered(r, "Preview", x + pw / 2, y + ph - u(18), footnote, label3);
		unequip.at(x + (pw - unequip.prefW()) / 2, y + ph + u(12), unequip.prefW(), u(28));
		ui.add(unequip).render(r, mx, my);
		// tiles
		float gx = x + pw + u(16);
		int cols = cols(w);
		String glyph = GLYPHS[tab.get()];
		for (int i = 0; i < TILES; i++) {
			float tx = gx + (i % cols) * u(96 + 12), ty = y + (i / cols) * u(120 + 12);
			Gfx.groupGlass(r, tx, ty, u(96), u(96), u(14), false);
			Gfx.iconAt(r, glyph, tx + u(48), ty + u(44), u(32), label3);
			Gfx.textMidCentered(r, "Coming soon", tx + u(48), ty + u(96 + 12), subheadline, label3);
		}
	}

	/** Flat front view of a blocky player silhouette (no skin texture hook yet). */
	private static void player(Renderer r, float cx, float cy, float px) {
		int skin = dark ? 0x3DFFFFFF : 0x29000000, shade = dark ? 0x29FFFFFF : 0x1F000000;
		float top = cy - 16 * px;
		Gfx.rect(r, cx - 4 * px, top, 8 * px, 8 * px, px * 1.2f, skin);
		Gfx.rect(r, cx - 4 * px, top + 8 * px, 8 * px, 12 * px, px * 0.6f, shade);
		Gfx.rect(r, cx - 8 * px, top + 8 * px, 4 * px, 12 * px, px * 0.8f, skin);
		Gfx.rect(r, cx + 4 * px, top + 8 * px, 4 * px, 12 * px, px * 0.8f, skin);
		Gfx.rect(r, cx - 4 * px, top + 20 * px, 4 * px, 12 * px, px * 0.8f, shade);
		Gfx.rect(r, cx, top + 20 * px, 4 * px, 12 * px, px * 0.8f, skin);
	}
}
