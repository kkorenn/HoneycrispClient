package dev.honeycrisp.module;

/** Critically damped or under-damped spring (exact solution, so any frame time is stable). */
public final class Spring {
	public double value, velocity, target;
	public double omega;
	public double damping;

	public Spring(double omega, double initial) {
		this(omega, 1, initial);
	}

	public Spring(double omega, double damping, double initial) {
		this.omega = omega;
		this.damping = damping;
		this.value = initial;
		this.target = initial;
	}

	public double update(double dt) {
		if (dt <= 0) return value;
		double delta = value - target;
		double z = Math.max(0.01, Math.min(1, damping));
		if (z < 1) {
			double zw = z * omega;
			double wd = omega * Math.sqrt(1 - z * z);
			double b = (velocity + zw * delta) / wd;
			double e = Math.exp(-zw * dt);
			double cos = Math.cos(wd * dt), sin = Math.sin(wd * dt);
			value = target + e * (delta * cos + b * sin);
			velocity = e * ((b * wd - zw * delta) * cos - (delta * wd + zw * b) * sin);
		} else {
			double c = velocity + omega * delta;
			double e = Math.exp(-omega * dt);
			value = target + (delta + c * dt) * e;
			velocity = (velocity - omega * c * dt) * e;
		}
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
