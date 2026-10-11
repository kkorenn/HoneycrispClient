package dev.honeycrisp.module;

/** Screen point a HUD element is attached to; the element's matching corner/edge sits at anchor + offset. */
public enum Anchor {
	TOP_LEFT(0, 0), TOP_CENTER(.5f, 0), TOP_RIGHT(1, 0),
	MIDDLE_LEFT(0, .5f), CENTER(.5f, .5f), MIDDLE_RIGHT(1, .5f),
	BOTTOM_LEFT(0, 1), BOTTOM_CENTER(.5f, 1), BOTTOM_RIGHT(1, 1);

	public final float fx, fy;

	Anchor(float fx, float fy) {
		this.fx = fx;
		this.fy = fy;
	}

	/** Left edge of an element of width w for this anchor's x factor and offset, clamped on screen. */
	public static float position(float factor, float offset, float size, float screen) {
		float p = factor * screen + offset - factor * size;
		return Math.max(0, Math.min(p, screen - size));
	}

	/** Inverse of {@link #position}: the offset that puts the element's left edge at p. */
	public static float offset(float factor, float p, float size, float screen) {
		return p + factor * size - factor * screen;
	}

	/** The anchor whose screen third contains the element's center. */
	public static Anchor nearest(float x, float y, float w, float h, float screenW, float screenH) {
		int col = third(x + w / 2, screenW), row = third(y + h / 2, screenH);
		return values()[row * 3 + col];
	}

	private static int third(float c, float size) {
		return c < size / 3 ? 0 : c < size * 2 / 3 ? 1 : 2;
	}
}
