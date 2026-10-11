package dev.honeycrisp.modules;

import dev.honeycrisp.module.BooleanSetting;
import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.KeybindSetting;
import dev.honeycrisp.module.Module;
import dev.honeycrisp.module.NumberSetting;
import dev.honeycrisp.module.Spring;
import dev.honeycrisp.platform.Keys;

/** Zoom = FOV divided by a factor, eased by a critically damped spring; scroll adjusts while zoomed. */
public final class ZoomModule extends Module {
	public static final double MAX_ZOOM = 50, SCROLL_STEP = 1.2;
	public final KeybindSetting key = setting(new KeybindSetting("key", "Zoom Key", Keys.C, KeybindSetting.Mode.HOLD));
	public final NumberSetting factor = setting(new NumberSetting("factor", "Zoom Factor", 4, 1.5, 20, 0.5));
	public final BooleanSetting smooth = setting(new BooleanSetting("smooth", "Smooth Animation", true));
	public final BooleanSetting scroll = setting(new BooleanSetting("scroll", "Scroll to Adjust", true));
	public final BooleanSetting scaleSensitivity = setting(new BooleanSetting("sensitivity", "Lower Sensitivity", true));

	private final Spring spring = new Spring(16, 1);
	private double scrollMul = 1;
	private boolean wasActive;
	private long last = -1;

	public ZoomModule() {
		super("zoom", "Zoom", "Hold a key to zoom in", Category.CAMERA, "plus.magnifyingglass");
		enabledByDefault();
	}

	public boolean active() {
		return enabled() && key.active();
	}

	public double targetZoom() {
		boolean a = active();
		if (a && !wasActive) scrollMul = 1;
		wasActive = a;
		return a ? Math.max(1, Math.min(MAX_ZOOM, factor.value() * scrollMul)) : 1;
	}

	/** Current zoom (1 = none), advanced to time now (ms). */
	public double zoom(long now) {
		double dt = last < 0 ? 0 : Math.min(0.1, (now - last) / 1000.0);
		last = now;
		spring.target = targetZoom();
		if (!smooth.on()) spring.snap(spring.target);
		else spring.update(dt);
		return Math.max(1, spring.value);
	}

	public double fovMultiplier(long now) {
		return 1 / zoom(now);
	}

	/** Uses the zoom of the last fovMultiplier call. */
	public double sensitivityMultiplier() {
		return scaleSensitivity.on() ? 1 / Math.max(1, spring.value) : 1;
	}

	/** Mouse wheel while zoomed (positive = in). Returns true when consumed. */
	public boolean scroll(double amount) {
		if (!active() || !scroll.on() || amount == 0) return false;
		double z = factor.value() * scrollMul * Math.pow(SCROLL_STEP, amount);
		scrollMul = Math.max(1, Math.min(MAX_ZOOM, z)) / factor.value();
		return true;
	}

	@Override
	protected void onDisable() {
		key.release();
		spring.snap(1);
	}
}
