package dev.honeycrisp.ui.widgets;

import dev.honeycrisp.platform.Renderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-screen input router. Screens call {@link #begin} at the start of render and {@link #add} for every widget
 * they draw (later = on top); input then goes to the topmost hit widget of the last frame, respecting clips.
 * Also owns the overlay (pop-up menu, popover) and the tooltip.
 */
public final class Ui {
	private final List<Widget> widgets = new ArrayList<Widget>();
	private final List<float[]> widgetClips = new ArrayList<float[]>();
	private final List<float[]> clips = new ArrayList<float[]>();
	private Widget focus, captured;
	private Widget overlay;
	/** The char event that follows a key a widget captured (key-bind) must not reach the screen. */
	private boolean swallowChar;
	private String tip, shownTip;
	private float tipX, tipY;
	private long tipSince, tipLastShown = Long.MIN_VALUE;

	public void begin() {
		widgets.clear();
		widgetClips.clear();
		clips.clear();
		tip = null;
	}

	public <W extends Widget> W add(W w) {
		widgets.add(w);
		widgetClips.add(clips.isEmpty() ? null : clips.get(clips.size() - 1));
		return w;
	}

	public void pushClip(Renderer r, float x, float y, float w, float h) {
		r.pushClip(x, y, w, h);
		if (!clips.isEmpty()) {
			float[] c = clips.get(clips.size() - 1);
			float x1 = Math.min(x + w, c[0] + c[2]), y1 = Math.min(y + h, c[1] + c[3]);
			x = Math.max(x, c[0]);
			y = Math.max(y, c[1]);
			w = Math.max(0, x1 - x);
			h = Math.max(0, y1 - y);
		}
		clips.add(new float[]{x, y, w, h});
	}

	public void popClip(Renderer r) {
		r.popClip();
		clips.remove(clips.size() - 1);
	}

	private boolean inClip(int i, float mx, float my) {
		float[] c = widgetClips.get(i);
		return c == null || (mx >= c[0] && mx < c[0] + c[2] && my >= c[1] && my < c[1] + c[3]);
	}

	// ---- focus

	public Widget focused() {
		return focus;
	}

	public void focus(Widget w) {
		if (focus == w) return;
		if (focus != null) {
			focus.focused = false;
			focus.focusLost();
		}
		focus = w;
		if (w != null) w.focused = true;
	}

	// ---- overlay (one at a time, drawn last, modal)

	public void openOverlay(Widget w) {
		overlay = w;
	}

	public void closeOverlay() {
		overlay = null;
	}

	public Widget overlay() {
		return overlay;
	}

	/** Draws and registers the overlay; call last in render. */
	public void renderOverlay(Renderer r, float mx, float my) {
		if (overlay != null) add(overlay).render(r, mx, my);
		drawTooltip(r);
	}

	// ---- input

	public boolean mouseClicked(float mx, float my, int button) {
		for (int i = widgets.size() - 1; i >= 0; i--) {
			Widget w = widgets.get(i);
			if (!w.enabled || !inClip(i, mx, my) || !w.hit(mx, my)) continue;
			if (w.mouseClicked(mx, my, button)) {
				captured = w;
				if (w.focusable()) focus(w);
				else if (w != overlay) focus(null);
				return true;
			}
		}
		focus(null);
		return false;
	}

	public boolean mouseReleased(float mx, float my, int button) {
		Widget c = captured;
		captured = null;
		if (c == null) return false;
		c.mouseReleased(mx, my, button);
		return true;
	}

	public boolean mouseDragged(float mx, float my, int button) {
		if (captured == null) return false;
		captured.mouseDragged(mx, my, button);
		return true;
	}

	public boolean mouseScrolled(float mx, float my, float amount) {
		for (int i = widgets.size() - 1; i >= 0; i--) {
			Widget w = widgets.get(i);
			if (w.enabled && inClip(i, mx, my) && w.scrollHit(mx, my) && w.mouseScrolled(mx, my, amount)) return true;
		}
		return false;
	}

	public boolean keyPressed(int key, int mods) {
		swallowChar = false;
		if (focus != null) {
			Widget f = focus;
			boolean used = f.keyPressed(key, mods);
			if (!f.focused && focus == f) {
				focus = null;
				f.focusLost();
				swallowChar = used;
			}
			if (used) return true;
		}
		return overlay != null && overlay.keyPressed(key, mods);
	}

	public boolean charTyped(char c) {
		if (swallowChar) {
			swallowChar = false;
			return true;
		}
		return focus != null && focus.charTyped(c);
	}

	// ---- tooltip (3.13)

	/** Requests a tooltip for this frame (call while the pointer is over the control). */
	public void tooltip(String text, float mx, float my) {
		tip = text;
		tipX = mx;
		tipY = my;
	}

	private void drawTooltip(Renderer r) {
		long now = Theme.now;
		if (tip == null) {
			if (shownTip != null) tipLastShown = now;
			shownTip = null;
			return;
		}
		if (!tip.equals(shownTip)) {
			boolean warm = tipLastShown != Long.MIN_VALUE && now - tipLastShown < 1500 || shownTip != null;
			shownTip = tip;
			tipSince = warm ? now - 700 : now;
		}
		float t = (now - tipSince - 700) / 120f;
		if (t <= 0) return;
		float a = 1 - (float) Math.pow(1 - Math.min(1, t), 3);
		float pw = Gfx.width(tip, Theme.subheadline) + Theme.u(16), ph = Gfx.lineHeight(Theme.subheadline) + Theme.u(10);
		float x = tipX, y = tipY + Theme.u(14);
		dev.honeycrisp.platform.Platform p = dev.honeycrisp.Honeycrisp.platform();
		float sw = p != null ? p.scaledWidth() : 1e6f, sh = p != null ? p.scaledHeight() : 1e6f;
		x = Math.max(Theme.u(4), Math.min(x, sw - pw - Theme.u(4)));
		if (y + ph > sh - Theme.u(4)) y = tipY - ph - Theme.u(8);
		float old = Gfx.opacity;
		Gfx.opacity = old * a;
		boolean rt = Theme.reduceTransparency;
		Theme.reduceTransparency = true;
		Gfx.windowGlass(r, x, y, pw, ph, Theme.u(6));
		Theme.reduceTransparency = rt;
		Gfx.textMid(r, tip, x + Theme.u(8), y + ph / 2, Theme.subheadline, Theme.label);
		Gfx.opacity = old;
	}
}
