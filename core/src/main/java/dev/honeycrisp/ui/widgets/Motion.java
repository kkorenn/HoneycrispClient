package dev.honeycrisp.ui.widgets;

import dev.honeycrisp.module.Spring;

/**
 * One animated value driven by Platform time ({@link Theme#now}). Uses module.Spring (critically damped) with
 * omega = 2π / response, so the DESIGN.md presets keep their response; ponytail: no under-damped bounce
 * (snappy 0.85 / bouncy 0.7 settle without overshoot), add a damped spring if the bounce is wanted.
 */
public final class Motion {
	public static final double QUICK = 0.20, SMOOTH = 0.40, SNAPPY = 0.30, BOUNCY = 0.45;
	private final Spring spring;
	private long last = Long.MIN_VALUE;

	public Motion(double response, double initial) {
		spring = new Spring(2 * Math.PI / response, initial);
	}

	/** Retargets (keeping velocity) and advances to the current frame time. */
	public float to(double target) {
		long now = Theme.now;
		double dt = last == Long.MIN_VALUE ? 0 : Math.min(0.05, Math.max(0, now - last) / 1000.0);
		last = now;
		spring.target = target;
		spring.update(dt);
		return (float) spring.value;
	}

	public float to(boolean on) {
		return to(on ? 1 : 0);
	}

	public float value() {
		return (float) spring.value;
	}

	public void snap(double v) {
		spring.snap(v);
		last = Theme.now;
	}
}
