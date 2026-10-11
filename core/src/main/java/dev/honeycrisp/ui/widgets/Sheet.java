package dev.honeycrisp.ui.widgets;

import dev.honeycrisp.platform.Keys;
import dev.honeycrisp.platform.Renderer;

import static dev.honeycrisp.ui.widgets.Theme.*;

/**
 * Modal sheet (3.19): scrim, centered L1 panel (r 24) with a 52u header (optional glyph well, title, trailing
 * accessory, close button), a scrolling body and a 56u footer. Subclasses draw the body and the footer buttons.
 */
public abstract class Sheet extends Widget {
	protected final Ui ui;
	private final Motion show = new Motion(Motion.SMOOTH, 0);
	private boolean open;
	protected final ScrollView scroll = new ScrollView();
	private final Button close = Button.icon("xmark", Button.Style.GLASS, new Runnable() {
		public void run() {
			close();
		}
	});
	public float maxWidth = 560;

	protected Sheet(Ui ui) {
		this.ui = ui;
	}

	public void open() {
		open = true;
		scroll.reset();
	}

	public void close() {
		open = false;
		if (ui.focused() != null) ui.focus(null);
	}

	public boolean isOpen() {
		return open;
	}

	public boolean visible() {
		return open || show.value() > 0.01f;
	}

	protected abstract String title();

	/** Glyph for the 32u header well, or null. */
	protected String glyph() {
		return null;
	}

	protected boolean glyphActive() {
		return true;
	}

	/** Body content height for a body width (px). */
	protected abstract float bodyHeight(float width);

	/** Draws the body; x/y = top-left inside the 16u padding. */
	protected abstract void renderBody(Renderer r, float x, float y, float width, float mx, float my);

	/** Header accessory left of the close button; returns its width (0 = none). */
	protected float renderAccessory(Renderer r, float right, float cy, float mx, float my) {
		return 0;
	}

	/** Footer buttons; right = right edge, cy = footer center. */
	protected abstract void renderFooter(Renderer r, float left, float right, float cy, float mx, float my);

	/** Return triggers this (the primary action). */
	protected void confirm() {
		close();
	}

	public void render(Renderer r, float sw, float sh, float mx, float my) {
		float t = show.to(open);
		if (!open && t < 0.01f) return;
		float old = Gfx.opacity;
		Gfx.opacity = old * Math.min(1, t);
		Gfx.fill(r, 0, 0, sw, sh, scrim);
		float pw = Math.min(u(maxWidth), sw * 0.8f), bw = pw - u(32);
		float bodyH = bodyHeight(bw) + u(32);
		float ph = Math.min(u(52 + 56) + bodyH, sh * 0.85f);
		float px = (sw - pw) / 2, py = (sh - ph) / 2 + (reduceMotion ? 0 : (1 - t) * u(12));
		at(px, py, pw, ph);
		ui.add(this);
		Gfx.windowGlass(r, px, py, pw, ph, u(24));
		// header
		float hy = py + u(26), tx = px + u(16);
		String g = glyph();
		if (g != null) {
			Gfx.rect(r, tx, hy - u(16), u(32), u(32), u(8), glyphActive() ? accent : fill2);
			Gfx.iconAt(r, g, tx + u(16), hy, u(18), glyphActive() ? onAccent : label2);
			tx += u(32 + 10);
		}
		close.at(px + pw - u(12 + 28), py + u(12), u(28), u(28));
		float acc = renderAccessory(r, close.x - u(12), hy, mx, my);
		Gfx.textMid(r, Gfx.truncate(title(), Theme.title3, close.x - u(12) - acc - tx - u(8)), tx, hy, Theme.title3, label);
		ui.add(close).render(r, mx, my);
		// body
		float top = py + u(52), viewH = ph - u(52 + 56);
		float by = scroll.begin(r, ui, px, top, pw, viewH, bodyH);
		renderBody(r, px + u(16), by + u(16), bw, mx, my);
		scroll.end(r, ui, mx, my);
		if (scroll.offset() > 0.5f) Gfx.fill(r, px, top, pw, hairline, separator);
		// footer
		float fy = py + ph - u(56);
		Gfx.fill(r, px, fy, pw, hairline, separator);
		renderFooter(r, px + u(16), px + pw - u(16), fy + u(28), mx, my);
		Gfx.opacity = old;
	}

	@Override
	public void render(Renderer r, float mx, float my) {
	}

	/** Catches every click: outside the panel closes the sheet. */
	@Override
	public boolean hit(float mx, float my) {
		return open;
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (!(mx >= x && mx < x + w && my >= y && my < y + h)) close();
		return true;
	}

	@Override
	public boolean mouseScrolled(float mx, float my, float amount) {
		return open;
	}

	/** Esc closes, Return confirms. */
	public boolean keyPressed(int key, int mods) {
		if (!open) return false;
		if (key == Keys.ESCAPE) close();
		else if (key == Keys.ENTER) confirm();
		else return false;
		return true;
	}
}
