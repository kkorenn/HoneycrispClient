package dev.honeycrisp.module;

public class NumberSetting extends Setting<Double> {
	public final double min, max, step;

	public NumberSetting(String id, String name, double defaultValue, double min, double max, double step) {
		super(id, name, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
	}

	public double value() {
		return value;
	}

	public float floatValue() {
		return (float) (double) value;
	}

	public int intValue() {
		return (int) Math.round(value);
	}

	public void set(double v) {
		set(Double.valueOf(v));
	}

	@Override
	protected Double sanitize(Double v) {
		if (v == null || v.isNaN()) return null;
		double d = Math.max(min, Math.min(max, v));
		if (step > 0) d = Math.min(max, min + Math.round((d - min) / step) * step);
		// Kill float noise from the step math (0.1 + 0.2 ...).
		return Math.round(d * 1e6) / 1e6;
	}

	/** 0..1 position of the value in [min, max] (sliders). */
	public double fraction() {
		return max > min ? (value - min) / (max - min) : 0;
	}

	public void setFraction(double f) {
		set(min + f * (max - min));
	}

	@Override
	public Object toJson() {
		return value;
	}

	@Override
	public void fromJson(Object json) {
		if (json instanceof Number) set(((Number) json).doubleValue());
	}
}
