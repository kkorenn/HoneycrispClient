package dev.honeycrisp.ui.widgets;

import dev.honeycrisp.platform.Renderer;

import static dev.honeycrisp.ui.widgets.Theme.*;

/** Switch (3.3): regular 38×22 or mini 26×15. Toggles on release; the knob stretches while pressed. */
public class Toggle extends Widget {
	private final Value<Boolean> value;
	public final boolean mini;
	private final Motion pos, press = new Motion(Motion.QUICK, 0);
	private boolean pressed;

	public Toggle(Value<Boolean> value, boolean mini) {
		this.value = value;
		this.mini = mini;
		pos = new Motion(Motion.SNAPPY, value.get() ? 1 : 0);
	}

	public float prefW() {
		return u(mini ? 26 : 38);
	}

	public float prefH() {
		return u(mini ? 15 : 22);
	}

	/** Places it with its right edge at right, vertically centered on cy. */
	public Toggle place(float right, float cy) {
		at(right - prefW(), cy - prefH() / 2, prefW(), prefH());
		return this;
	}

	public void toggle() {
		value.set(!value.get());
	}

	@Override
	public void render(Renderer r, float mx, float my) {
		float t = pos.to(value.get()), p = press.to(pressed);
		float old = Gfx.opacity;
		if (!enabled) Gfx.opacity *= 0.4f;
		Gfx.capsule(r, x, y, w, h, mix(fill1, accent, t));
		float inset = u(2), kh = h - 2 * inset, kw = kh + p * u(mini ? 4 : 6);
		float kx = x + inset + t * (w - 2 * inset - kw);
		Gfx.knob(r, kx, y + inset, kw, kh, pressed);
		if (hovered(mx, my) && !pressed) Gfx.capsule(r, x, y, w, h, hoverOverlay());
		Gfx.opacity = old;
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (button != 0) return false;
		pressed = true;
		return true;
	}

	@Override
	public void mouseReleased(float mx, float my, int button) {
		if (pressed && hit(mx, my)) toggle();
		pressed = false;
	}
}
