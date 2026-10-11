package dev.honeycrisp.ui.widgets;

import dev.honeycrisp.platform.Renderer;

import static dev.honeycrisp.ui.widgets.Theme.*;

/**
 * Vertical scroll view with an overlay scroller (3.18). Usage per frame:
 * {@code float top = sv.begin(r, ui, x, y, w, h, contentH); ...draw content at top...; sv.end(r, ui, mx, my);}
 * ponytail: clamps at the edges instead of rubber-banding; add the 3.18 rubber band if it's missed.
 */
public class ScrollView extends Widget {
	private final Motion pos = new Motion(Motion.QUICK, 0);
	private final Motion thumbWide = new Motion(Motion.QUICK, 0);
	private float target, content;
	private long lastScroll = Long.MIN_VALUE;
	private boolean dragging;
	private float dragOffset;

	public float max() {
		return Math.max(0, content - h);
	}

	public float offset() {
		return pos.value();
	}

	public void scrollTo(float v) {
		target = Math.max(0, Math.min(max(), v));
	}

	public void reset() {
		target = 0;
		pos.snap(0);
	}

	/** Pushes the clip and returns the content's top y. */
	public float begin(Renderer r, Ui ui, float x, float y, float w, float h, float contentH) {
		at(x, y, w, h);
		content = contentH;
		target = Math.max(0, Math.min(max(), target));
		float o = pos.to(target);
		ui.pushClip(r, x, y, w, h);
		return y - o;
	}

	public void end(Renderer r, Ui ui, float mx, float my) {
		ui.popClip(r);
		ui.add(this);
		if (max() <= 0) return;
		boolean near = mx >= x + w - u(16) && mx < x + w && my >= y && my < y + h;
		long idle = now - lastScroll;
		float vis = dragging || near ? 1 : lastScroll == Long.MIN_VALUE ? 0 : idle < 800 ? 1 : Math.max(0, 1 - (idle - 800) / 300f);
		if (vis <= 0) return;
		float wide = thumbWide.to(dragging || near), tw = u(6 + 4 * wide);
		float[] t = thumb();
		float old = Gfx.opacity;
		Gfx.opacity *= vis;
		if (wide > 0.01f) Gfx.capsule(r, x + w - u(3) - tw, y + u(3), tw, h - u(6), alpha(fill4, wide));
		Gfx.capsule(r, x + w - u(3) - tw, t[0], tw, t[1], dark ? 0x80FFFFFF : 0x80000000);
		Gfx.opacity = old;
	}

	/** {y, length} of the thumb. */
	private float[] thumb() {
		float track = h - u(6), len = Math.max(u(32), track * h / content);
		float ty = y + u(3) + (track - len) * (max() > 0 ? pos.value() / max() : 0);
		return new float[]{ty, len};
	}

	@Override
	public boolean hit(float mx, float my) {
		return max() > 0 && mx >= x + w - u(16) && mx < x + w && my >= y && my < y + h;
	}

	@Override
	public boolean scrollHit(float mx, float my) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	@Override
	public boolean mouseScrolled(float mx, float my, float amount) {
		if (max() <= 0) return false;
		scrollTo(target - amount * u(40));
		lastScroll = now;
		return true;
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		float[] t = thumb();
		dragging = true;
		dragOffset = my >= t[0] && my < t[0] + t[1] ? my - t[0] : t[1] / 2;
		mouseDragged(mx, my, button);
		return true;
	}

	@Override
	public void mouseDragged(float mx, float my, int button) {
		float[] t = thumb();
		float track = h - u(6) - t[1];
		if (track > 0) {
			scrollTo((my - dragOffset - y - u(3)) / track * max());
			pos.snap(target);
		}
		lastScroll = now;
	}

	@Override
	public void mouseReleased(float mx, float my, int button) {
		dragging = false;
	}

	@Override
	public void render(Renderer r, float mx, float my) {
	}
}
