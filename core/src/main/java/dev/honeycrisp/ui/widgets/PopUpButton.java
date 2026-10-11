package dev.honeycrisp.ui.widgets;

import dev.honeycrisp.platform.Keys;
import dev.honeycrisp.platform.Renderer;

import static dev.honeycrisp.ui.widgets.Theme.*;

/** Pop-up button (3.17): opens a menu (3.15) with the selected item placed over the button. */
public class PopUpButton extends Widget {
	private final Ui ui;
	private final Value<Integer> value;
	private final String[] labels;

	public PopUpButton(Ui ui, Value<Integer> value, String... labels) {
		this.ui = ui;
		this.value = value;
		this.labels = labels;
	}

	public float prefW() {
		float m = 0;
		for (String l : labels) m = Math.max(m, Gfx.width(l, Theme.calloutMedium));
		return Math.max(u(120), m + u(14 + 28));
	}

	public PopUpButton place(float right, float cy) {
		at(right - prefW(), cy - u(14), prefW(), u(28));
		return this;
	}

	@Override
	public void render(Renderer r, float mx, float my) {
		float rad = u(8);
		Gfx.rect(r, x, y, w, h, rad, fill2);
		Gfx.stroke(r, x, y, w, h, rad, hairline, dark ? 0x14FFFFFF : 0x0F000000);
		if (hovered(mx, my)) Gfx.rect(r, x, y, w, h, rad, hoverOverlay());
		Gfx.textMid(r, labels[value.get()], x + u(14), y + h / 2, Theme.calloutMedium, label);
		Gfx.iconAt(r, "chevron.up.chevron.down", x + w - u(18), y + h / 2, u(10), label2);
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (button != 0) return false;
		ui.openOverlay(new Menu());
		return true;
	}

	private final class Menu extends Widget {
		private int hover = -1;

		Menu() {
			float iw = u(24), pad = u(5), mw = Math.max(u(180), PopUpButton.this.w + u(10));
			for (String l : labels) mw = Math.max(mw, Gfx.width(l, Theme.body) + u(48 + 10));
			float mh = labels.length * iw + 2 * pad;
			float my = PopUpButton.this.y + PopUpButton.this.h / 2 - pad - value.get() * iw - iw / 2;
			at(PopUpButton.this.x - u(5), my, mw, mh);
		}

		@Override
		public boolean hit(float mx, float my) {
			return true;
		}

		@Override
		public void render(Renderer r, float mx, float my) {
			dev.honeycrisp.platform.Platform p = dev.honeycrisp.Honeycrisp.platform();
			if (p != null) y = Math.max(u(4), Math.min(y, p.scaledHeight() - h - u(4)));
			Gfx.windowGlass(r, x, y, w, h, u(12));
			float iw = u(24), pad = u(5);
			hover = mx >= x && mx < x + w ? (int) Math.floor((my - y - pad) / iw) : -1;
			for (int i = 0; i < labels.length; i++) {
				float iy = y + pad + i * iw;
				boolean hl = i == hover;
				if (hl) Gfx.rect(r, x + pad, iy, w - 2 * pad, iw, u(7), accent);
				int fg = hl ? onAccent : label;
				if (i == value.get()) Gfx.iconAt(r, "checkmark", x + pad + u(10 + 6), iy + iw / 2, u(12), fg);
				Gfx.textMid(r, labels[i], x + pad + u(27), iy + iw / 2, Theme.body, fg);
			}
		}

		@Override
		public boolean mouseClicked(float mx, float my, int button) {
			if (mx >= x && mx < x + w && my >= y && my < y + h) {
				int i = (int) Math.floor((my - y - u(5)) / u(24));
				if (i >= 0 && i < labels.length) value.set(i);
			}
			ui.closeOverlay();
			return true;
		}

		@Override
		public boolean keyPressed(int key, int mods) {
			if (key == Keys.ESCAPE) ui.closeOverlay();
			return key == Keys.ESCAPE;
		}
	}
}
