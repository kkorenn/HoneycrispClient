package dev.honeycrisp.ui.widgets;

import dev.honeycrisp.module.NumberSetting;
import dev.honeycrisp.platform.Keys;
import dev.honeycrisp.platform.Renderer;

import static dev.honeycrisp.ui.widgets.Theme.*;

/** Slider (3.4): 4u capsule track, 22×14 thumb, ticks for ≤ 11 values. Height = 24u hit area. */
public class Slider extends Widget {
	private final NumberSetting s;
	private final Motion press = new Motion(Motion.QUICK, 0);
	private boolean dragging;
	/** Show the value as a percentage of 1.0 (UI scale). */
	public boolean percent;

	public Slider(NumberSetting s) {
		this.s = s;
	}

	public NumberSetting setting() {
		return s;
	}

	private int steps() {
		return s.step > 0 ? (int) Math.round((s.max - s.min) / s.step) : 0;
	}

	@Override
	public void render(Renderer r, float mx, float my) {
		float old = Gfx.opacity;
		if (!enabled) Gfx.opacity *= 0.4f;
		float th = u(4), cy = y + h / 2, tw = u(22), tH = u(14);
		float f = (float) s.fraction(), travel = w - tw, tx = x + f * travel;
		Gfx.capsule(r, x, cy - th / 2, w, th, fill1);
		Gfx.capsule(r, x, cy - th / 2, Math.max(th, tx + tw / 2 - x), th, accent);
		int n = steps();
		if (n > 0 && n <= 10) for (int i = 0; i <= n; i++) Gfx.fill(r, x + tw / 2 + travel * i / n - u(1), cy + th / 2 + u(3), u(2), u(6), label3);
		float p = press.to(dragging), sc = 1 + 0.15f * p;
		float kw = tw * sc, kh = tH * sc;
		Gfx.knob(r, tx + (tw - kw) / 2, cy - kh / 2, kw, kh, dragging);
		Gfx.opacity = old;
	}

	@Override
	public boolean hit(float mx, float my) {
		return mx >= x && mx < x + w && my >= y + h / 2 - u(12) && my < y + h / 2 + u(12);
	}

	private void setFrom(float mx) {
		float tw = u(22);
		s.setFraction(Math.max(0, Math.min(1, (mx - x - tw / 2) / (w - tw))));
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (button != 0) return false;
		dragging = true;
		setFrom(mx);
		return true;
	}

	@Override
	public void mouseDragged(float mx, float my, int button) {
		if (dragging) setFrom(mx);
	}

	@Override
	public void mouseReleased(float mx, float my, int button) {
		dragging = false;
	}

	@Override
	public boolean focusable() {
		return true;
	}

	@Override
	public boolean keyPressed(int key, int mods) {
		double step = s.step > 0 ? s.step : (s.max - s.min) / 100;
		if ((mods & 1) != 0) step *= 10;
		if (key == Keys.LEFT) s.set(s.value() - step);
		else if (key == Keys.RIGHT) s.set(s.value() + step);
		else return false;
		return true;
	}

	/** Value label text: "1.25×" for scales, "40%" for 0..1 fractions, else as-is. */
	public String format() {
		return percent ? Math.round(s.value() * 100) + "%" : format(s);
	}

	public static String format(NumberSetting s) {
		double v = s.value();
		if (s.name.toLowerCase().contains("scale")) return trim(v) + "×";
		if (s.min >= 0 && s.max <= 1 && s.step < 1) return Math.round(v * 100) + "%";
		return trim(v);
	}

	private static String trim(double v) {
		if (v == Math.rint(v)) return String.valueOf((long) v);
		String t = String.format(java.util.Locale.ROOT, "%.2f", v);
		while (t.endsWith("0")) t = t.substring(0, t.length() - 1);
		return t;
	}
}
