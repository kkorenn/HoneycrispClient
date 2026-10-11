package dev.honeycrisp.ui.widgets;

import dev.honeycrisp.platform.Renderer;

/**
 * A control laid out by its screen every frame ({@link #at}), drawn with {@link #render} and registered with
 * {@link Ui#add} so the screen's input reaches it. Coordinates are GUI px.
 */
public abstract class Widget {
	public float x, y, w, h;
	public boolean enabled = true;
	/** Set by Ui while this widget has keyboard focus; a widget clears it to give focus up. */
	protected boolean focused;

	public Widget at(float x, float y, float w, float h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		return this;
	}

	/** Natural size, used by forms to place trailing controls. */
	public float prefW() {
		return w;
	}

	public float prefH() {
		return h;
	}

	/** Hit test, expanded to the 24u minimum target. */
	public boolean hit(float mx, float my) {
		float min = Theme.u(24), ex = Math.max(0, (min - w) / 2), ey = Math.max(0, (min - h) / 2);
		return mx >= x - ex && mx < x + w + ex && my >= y - ey && my < y + h + ey;
	}

	/** Area that receives the scroll wheel. */
	public boolean scrollHit(float mx, float my) {
		return hit(mx, my);
	}

	public boolean hovered(float mx, float my) {
		return enabled && hit(mx, my);
	}

	public abstract void render(Renderer r, float mx, float my);

	public boolean mouseClicked(float mx, float my, int button) {
		return false;
	}

	public void mouseReleased(float mx, float my, int button) {
	}

	public void mouseDragged(float mx, float my, int button) {
	}

	public boolean mouseScrolled(float mx, float my, float amount) {
		return false;
	}

	public boolean keyPressed(int key, int mods) {
		return false;
	}

	public boolean charTyped(char c) {
		return false;
	}

	/** Takes keyboard focus when clicked (text fields, key capture). */
	public boolean focusable() {
		return false;
	}

	public boolean isFocused() {
		return focused;
	}

	protected void focusLost() {
	}
}
