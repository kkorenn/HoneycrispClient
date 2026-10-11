package dev.honeycrisp.ui.widgets;

import dev.honeycrisp.platform.Renderer;

import static dev.honeycrisp.ui.widgets.Theme.*;

/** Segmented control (3.2): 28u capsule track, sliding L3 thumb. */
public class Segmented extends Widget {
	private final Value<Integer> value;
	private final String[] labels;
	private final Motion thumb;

	public Segmented(Value<Integer> value, String... labels) {
		this.value = value;
		this.labels = labels;
		thumb = new Motion(Motion.SNAPPY, value.get());
	}

	public static Segmented of(dev.honeycrisp.module.ChoiceSetting<?> s) {
		Object[] c = s.choices();
		String[] labels = new String[c.length];
		for (int i = 0; i < c.length; i++) labels[i] = c[i].toString();
		return new Segmented(indexOf(s), labels);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Value<Integer> indexOf(dev.honeycrisp.module.ChoiceSetting s) {
		return Value.index(s);
	}

	private float segW() {
		float m = u(64);
		for (String l : labels) m = Math.max(m, Gfx.width(l, Theme.callout.withWeight(600)) + u(24));
		return m;
	}

	public float prefW() {
		return segW() * labels.length;
	}

	public float prefH() {
		return u(28);
	}

	public Segmented place(float right, float cy) {
		at(right - prefW(), cy - prefH() / 2, prefW(), prefH());
		return this;
	}

	@Override
	public void render(Renderer r, float mx, float my) {
		float old = Gfx.opacity;
		if (!enabled) Gfx.opacity *= 0.4f;
		int sel = value.get(), n = labels.length;
		float sw = w / n, t = thumb.to(sel), in = u(2);
		Gfx.capsule(r, x, y, w, h, fill3);
		int hov = hovered(mx, my) ? (int) ((mx - x) / sw) : -1;
		if (hov >= 0 && hov < n && hov != sel) Gfx.capsule(r, x + hov * sw + in, y + in, sw - 2 * in, h - 2 * in, fill4);
		for (int i = 1; i < n; i++) {
			if (Math.abs(i - t) < 1 || Math.abs(i - 1 - t) < 1) continue;
			Gfx.fill(r, x + i * sw - hairline / 2, y + (h - u(12)) / 2, hairline, u(12), separator);
		}
		Gfx.controlGlass(r, x + t * sw + in, y + in, sw - 2 * in, h - 2 * in, (h - 2 * in) / 2);
		for (int i = 0; i < n; i++) {
			boolean s = i == sel;
			Gfx.textMidCentered(r, labels[i], x + i * sw + sw / 2, y + h / 2, s ? Theme.callout.withWeight(600) : Theme.calloutMedium, s ? label : label2);
		}
		Gfx.opacity = old;
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (button != 0) return false;
		int i = (int) ((mx - x) / (w / labels.length));
		if (i >= 0 && i < labels.length) value.set(i);
		return true;
	}
}
