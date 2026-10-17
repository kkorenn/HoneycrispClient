package dev.honeycrisp.ui.widgets;

import dev.honeycrisp.platform.Renderer;
import dev.honeycrisp.ui.draw.Font;

import static dev.honeycrisp.ui.widgets.Theme.*;

/** Buttons (3.8). Glyph first with a 6u gap; label centered unless {@link #leftAligned}. Fires on release. */
public class Button extends Widget {
	public enum Size {
		SMALL(24, 6, 10, 12), REGULAR(28, 8, 14, 14), LARGE(36, -1, 20, 16), XLARGE(48, -1, 28, 20);

		final float height, radius, padding, glyph;

		Size(float height, float radius, float padding, float glyph) {
			this.height = height;
			this.radius = radius;
			this.padding = padding;
			this.glyph = glyph;
		}
	}

	public enum Style {
		PRIMARY, SECONDARY, DESTRUCTIVE, DESTRUCTIVE_CONFIRM, PLAIN, PLAIN_ACCENT, PLAIN_DESTRUCTIVE,
		/** L3 glass body (over the world: HUD editor toolbar, circular icon buttons). */
		GLASS,
		/** Clear glass over the main-menu panorama. */
		CLEAR
	}

	public String label, glyph;
	public Size size;
	public Style style;
	public Runnable action;
	public boolean circular, leftAligned, selected;
	private final Motion hover = new Motion(Motion.QUICK, 0), press = new Motion(Motion.QUICK, 0);
	private boolean pressed;

	public Button(String label, String glyph, Size size, Style style, Runnable action) {
		this.label = label;
		this.glyph = glyph;
		this.size = size;
		this.style = style;
		this.action = action;
	}

	/** Circular icon button (28u L3 glass, 14 box glyph). */
	public static Button icon(String glyph, Style style, Runnable action) {
		Button b = new Button(null, glyph, Size.REGULAR, style, action);
		b.circular = true;
		return b;
	}

	public Font font() {
		switch (size) {
			case LARGE: return Theme.bodyBold;
			case XLARGE: return Theme.title3;
			default: return Theme.calloutMedium;
		}
	}

	public float prefH() {
		return u(size.height);
	}

	public float prefW() {
		if (circular || label == null) return prefH();
		float cw = Gfx.width(label, font());
		if (glyph != null) cw += u(size.glyph + 6);
		return cw + 2 * u(size.padding);
	}

	public Button place(float x, float y) {
		at(x, y, prefW(), prefH());
		return this;
	}

	private float radius() {
		return circular || size.radius < 0 ? h / 2 : u(size.radius);
	}

	@Override
	public void render(Renderer r, float mx, float my) {
		float old = Gfx.opacity;
		if (!enabled) Gfx.opacity *= 0.4f;
		boolean hov = hovered(mx, my);
		float hv = hover.to(hov && !pressed), pr = press.to(pressed && hov);
		float s = 0.015f * pr, bx = x + w * s, by = y + h * s, bw = w * (1 - 2 * s), bh = h * (1 - 2 * s), rad = radius() * (1 - 2 * s);
		int fg;
		switch (style) {
			case PRIMARY:
				Gfx.controlGlass(r, bx, by, bw, bh, rad);
				Gfx.rect(r, bx, by, bw, bh, rad, reduceTransparency ? accent : alpha(accent, 0.85f));
				Gfx.stroke(r, bx, by, bw, bh, rad, u(1), 0x26FFFFFF);
				Gfx.rect(r, bx, by, bw, bh, rad, alpha(0xFFFFFFFF, 0.08f * hv));
				Gfx.rect(r, bx, by, bw, bh, rad, alpha(0x26000000, pr));
				fg = onAccent;
				break;
			case DESTRUCTIVE_CONFIRM:
				Gfx.controlGlass(r, bx, by, bw, bh, rad);
				Gfx.rect(r, bx, by, bw, bh, rad, reduceTransparency ? red : alpha(red, 0.85f));
				Gfx.rect(r, bx, by, bw, bh, rad, alpha(0xFFFFFFFF, 0.08f * hv));
				fg = 0xFFFFFFFF;
				break;
			case PLAIN:
			case PLAIN_ACCENT:
			case PLAIN_DESTRUCTIVE:
				Gfx.rect(r, bx, by, bw, bh, rad, alpha(fill4, hv + pr));
				fg = style == Style.PLAIN_ACCENT ? accent : style == Style.PLAIN_DESTRUCTIVE ? red : Theme.label2;
				break;
			case GLASS:
				Gfx.controlGlass(r, bx, by, bw, bh, rad);
				fg = Theme.label;
				break;
			case CLEAR:
				Gfx.clearGlass(r, bx, by, bw, bh, rad);
				fg = 0xF2FFFFFF;
				break;
			default:
				Gfx.controlGlass(r, bx, by, bw, bh, rad);
				fg = style == Style.DESTRUCTIVE ? red : Theme.label;
		}
		if (selected) {
			Gfx.rect(r, bx, by, bw, bh, rad, accentTint);
			fg = accent;
		}
		if (style.ordinal() < Style.PLAIN.ordinal() && style != Style.PRIMARY || style == Style.GLASS || style == Style.CLEAR) {
			Gfx.rect(r, bx, by, bw, bh, rad, alpha(style == Style.CLEAR ? 0x0FFFFFFF : hoverOverlay(), hv));
			Gfx.rect(r, bx, by, bw, bh, rad, alpha(style == Style.CLEAR ? 0x1FFFFFFF : pressedOverlay(), pr));
		}
		float gs = u(circular ? 14 : size.glyph), cy = y + h / 2;
		if (label == null || circular) {
			if (glyph != null) Gfx.iconAt(r, glyph, x + w / 2, cy, gs, style == Style.PLAIN && !selected ? label2 : fg);
		} else {
			Font f = font();
			float cw = Gfx.width(label, f) + (glyph != null ? gs + u(6) : 0);
			float cx = leftAligned ? x + u(size.padding) : x + (w - cw) / 2;
			if (glyph != null) {
				Gfx.iconAt(r, glyph, cx + gs / 2, cy, gs, fg);
				cx += gs + u(6);
			}
			Gfx.textMid(r, Gfx.truncate(label, f, x + w - cx - u(size.padding / 2)), cx, cy, f, fg);
		}
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
		boolean fire = pressed && hit(mx, my);
		pressed = false;
		if (fire && action != null) action.run();
	}

	/** Programmatic press (Return on the primary button). */
	public void fire() {
		if (enabled && action != null) action.run();
	}
}
