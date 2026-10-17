package dev.honeycrisp.modules;

import dev.honeycrisp.module.Anchor;
import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.NumberSetting;
import dev.honeycrisp.module.HudModule;
import dev.honeycrisp.platform.GameInfo;
import dev.honeycrisp.platform.Renderer;

/** A horizontal heading strip (N at 0°, E at 90°) centered on where the player looks. */
public final class CompassModule extends HudModule {
	private static final String[] LABELS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
	public final NumberSetting stripWidth = setting(new NumberSetting("width", "Width", 160, 80, 320, 10));
	public final NumberSetting range = setting(new NumberSetting("range", "Visible Degrees", 180, 90, 360, 15));

	public CompassModule() {
		super("compass", "Compass", "Direction strip", Category.HUD, "location.north.circle", Anchor.TOP_CENTER, 0, 20);
	}

	/** Compass bearing 0..360 (0 = north, 90 = east) from Minecraft yaw (0 = south, 90 = west). */
	public static double bearing(float yaw) {
		double b = (yaw + 180.0) % 360.0;
		return b < 0 ? b + 360 : b;
	}

	@Override
	protected void measure(GameInfo game) {
		size(stripWidth.floatValue(), lineHeight() + 8);
	}

	@Override
	protected void render(Renderer renderer, GameInfo game, float partialTick) {
		double heading = preview ? 30 : bearing(game.yaw());
		float w = stripWidth.floatValue(), cx = w / 2, h = lineHeight() + 8;
		double pxPerDeg = w / range.value(), half = range.value() / 2;
		int c = color(), dim = (c & 0xFFFFFF) | (((c >>> 24) / 2) << 24);
		for (int b = 0; b < 360; b += 15) {
			double d = ((b - heading) % 360 + 540) % 360 - 180; // signed angle -180..180
			if (Math.abs(d) > half - 4) continue;
			float x = (float) (cx + d * pxPerDeg);
			if (b % 45 == 0) textCentered(LABELS[b / 45], x, 3, b == 0 ? 0xFFFF5555 : c);
			else fill(x - 0.5f, h - 6, 1, 3, dim);
		}
		fill(cx - 0.5f, h - 3, 1, 3, c);
		fill(cx - 1.5f, h - 1.5f, 3, 1.5f, c);
	}
}
