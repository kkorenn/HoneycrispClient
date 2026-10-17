package dev.honeycrisp.modules;

import dev.honeycrisp.module.Anchor;
import dev.honeycrisp.module.BooleanSetting;
import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.TextHudModule;
import dev.honeycrisp.platform.GameInfo;

import java.util.Locale;

/** Blocks per second, averaged over the last 10 ticks of position deltas. */
public final class SpeedModule extends TextHudModule {
	private static final int TICKS = 10;
	public final BooleanSetting vertical = setting(new BooleanSetting("vertical", "Include Vertical", false));
	private final double[] deltas = new double[TICKS];
	private int n;
	private double lx, ly, lz;
	private boolean has;

	public SpeedModule() {
		super("speed", "Speed", "Movement speed in blocks per second", Category.HUD, "gauge.with.dots.needle.67percent", Anchor.TOP_LEFT, 4, 112);
	}

	@Override
	public void onTick(GameInfo game) {
		if (!game.inWorld()) {
			has = false;
			return;
		}
		tick(game.x(), game.y(), game.z());
	}

	void tick(double x, double y, double z) {
		double d = 0;
		if (has) {
			double dx = x - lx, dy = vertical.on() ? y - ly : 0, dz = z - lz;
			d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (d > 10) d = 0; // teleport
		}
		deltas[n++ % TICKS] = d;
		lx = x;
		ly = y;
		lz = z;
		has = true;
	}

	/** Blocks per second (20 ticks/s). */
	public double speed() {
		int k = Math.min(n, TICKS);
		if (k == 0) return 0;
		double sum = 0;
		for (int i = 0; i < k; i++) sum += deltas[i];
		return sum / k * 20;
	}

	@Override
	protected void onDisable() {
		n = 0;
		has = false;
	}

	@Override
	protected String text(GameInfo game) {
		return String.format(Locale.ROOT, "%.2f m/s", speed());
	}

	@Override
	protected String previewText() {
		return "5.61 m/s";
	}
}
