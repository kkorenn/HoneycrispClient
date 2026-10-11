package dev.honeycrisp.modules;

import dev.honeycrisp.hooks.Crosshair;
import dev.honeycrisp.module.BooleanSetting;
import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.ChoiceSetting;
import dev.honeycrisp.module.ColorSetting;
import dev.honeycrisp.module.Module;
import dev.honeycrisp.module.NumberSetting;
import dev.honeycrisp.platform.Renderer;

/** Crosshair drawn only with Renderer.fill (circles as half-pixel scanlines). */
public final class CrosshairModule extends Module implements Crosshair {
	public enum Style {
		CROSS("Cross"), DOT("Dot"), CIRCLE("Circle"), T("T");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private static final float OUTLINE = 0.5f;
	public final ChoiceSetting<Style> style = setting(new ChoiceSetting<Style>("style", "Style", Style.CROSS));
	public final NumberSetting size = setting(new NumberSetting("size", "Size", 5, 1, 15, 0.5));
	public final NumberSetting gap = setting(new NumberSetting("gap", "Gap", 2, 0, 10, 0.5));
	public final NumberSetting thickness = setting(new NumberSetting("thickness", "Thickness", 1, 0.5, 4, 0.5));
	public final ColorSetting color = setting(new ColorSetting("color", "Color", 0xFFFFFFFF));
	public final BooleanSetting outline = setting(new BooleanSetting("outline", "Outline", true));
	public final BooleanSetting centerDot = setting(new BooleanSetting("dot", "Center Dot", false));

	public CrosshairModule() {
		super("crosshair", "Crosshair", "Custom crosshair", Category.PVP, "scope");
	}

	@Override
	public void draw(Renderer r, float cx, float cy) {
		int c = color.argb();
		int o = ((c >>> 24) * 3 / 4) << 24;
		if (outline.on()) shape(r, cx, cy, OUTLINE, o);
		shape(r, cx, cy, 0, c);
	}

	/** grow > 0 draws the outline pass: every part expanded by that much. */
	private void shape(Renderer r, float cx, float cy, float grow, int argb) {
		float s = size.floatValue(), g = gap.floatValue(), t = thickness.floatValue(), h = t / 2;
		switch (style.get()) {
			case DOT:
				disc(r, cx, cy, t + grow, argb);
				return;
			case CIRCLE:
				ring(r, cx, cy, g + t + grow, Math.max(0, g - grow), argb);
				break;
			default:
				if (style.get() != Style.T) r.fill(cx - h - grow, cy - g - s - grow, t + 2 * grow, s + 2 * grow, argb);
				r.fill(cx - h - grow, cy + g - grow, t + 2 * grow, s + 2 * grow, argb);
				r.fill(cx - g - s - grow, cy - h - grow, s + 2 * grow, t + 2 * grow, argb);
				r.fill(cx + g - grow, cy - h - grow, s + 2 * grow, t + 2 * grow, argb);
		}
		if (centerDot.on()) disc(r, cx, cy, h + grow, argb);
	}

	static void disc(Renderer r, float cx, float cy, float radius, int argb) {
		ring(r, cx, cy, radius, 0, argb);
	}

	/** Annulus between inner and outer radius, as 0.5 px tall rows. */
	static void ring(Renderer r, float cx, float cy, float outer, float inner, int argb) {
		final float step = 0.5f;
		for (float y = -outer; y < outer; y += step) {
			float my = y + step / 2;
			float xo = (float) Math.sqrt(Math.max(0, outer * outer - my * my));
			if (xo <= 0) continue;
			if (Math.abs(my) < inner) {
				float xi = (float) Math.sqrt(inner * inner - my * my);
				r.fill(cx - xo, cy + y, xo - xi, step, argb);
				r.fill(cx + xi, cy + y, xo - xi, step, argb);
			} else {
				r.fill(cx - xo, cy + y, 2 * xo, step, argb);
			}
		}
	}
}
