package dev.honeycrisp.module;

/** Critically damped spring (exact solution, so any frame time is stable). omega ≈ 4 / settle time in seconds. */
public final class Spring {
	public double value, velocity, target;
	public double omega;

	public Spring(double omega, double initial) {
		this.omega = omega;
		this.value = initial;
		this.target = initial;
	}

	public double update(double dt) {
		if (dt <= 0) return value;
		double delta = value - target;
		double c = velocity + omega * delta;
		double e = Math.exp(-omega * dt);
		value = target + (delta + c * dt) * e;
		velocity = (velocity - omega * c * dt) * e;
		if (Math.abs(value - target) < 1e-5 && Math.abs(velocity) < 1e-4) {
			value = target;
			velocity = 0;
		}
		return value;
	}

	public void snap(double v) {
		value = target = v;
		velocity = 0;
	}
}
