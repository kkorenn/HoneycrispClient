package dev.honeycrisp.modules;

import dev.honeycrisp.module.Anchor;
import dev.honeycrisp.module.BooleanSetting;
import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.TextHudModule;
import dev.honeycrisp.platform.GameInfo;

import java.util.Locale;

public final class CoordinatesModule extends TextHudModule {
	private static final String[] DIRS = {"South", "West", "North", "East"};
	private static final String[] AXES = {"+Z", "-X", "-Z", "+X"};

	public final BooleanSetting decimals = setting(new BooleanSetting("decimals", "Decimals", false));
	public final BooleanSetting facing = setting(new BooleanSetting("facing", "Show Facing", true));
	public final BooleanSetting biome = setting(new BooleanSetting("biome", "Show Biome", false));

	public CoordinatesModule() {
		super("coords", "Coordinates", "Position, facing and biome", Category.HUD, "mappin.and.ellipse", Anchor.TOP_LEFT, 4, 58);
		enabledByDefault();
	}

	@Override
	protected String text(GameInfo game) {
		return build(game.x(), game.y(), game.z(), game.yaw(), game.biome());
	}

	@Override
	protected String previewText() {
		return build(128.5, 64, -312.25, 180, "Plains");
	}

	private String build(double x, double y, double z, float yaw, String biomeName) {
		StringBuilder sb = new StringBuilder();
		sb.append("X: ").append(num(x)).append("\nY: ").append(num(y)).append("\nZ: ").append(num(z));
		if (facing.on()) {
			int d = facingIndex(yaw);
			sb.append('\n').append(DIRS[d]).append(" (").append(AXES[d]).append(')');
		}
		if (biome.on() && biomeName != null && !biomeName.isEmpty()) sb.append('\n').append(biomeName);
		return sb.toString();
	}

	private String num(double v) {
		return decimals.on() ? String.format(Locale.ROOT, "%.1f", v) : Long.toString((long) Math.floor(v));
	}

	/** 0 = south, 1 = west, 2 = north, 3 = east (Minecraft yaw: 0 = south, 90 = west). */
	public static int facingIndex(float yaw) {
		return (int) Math.floor(yaw / 90.0 + 0.5) & 3;
	}
}
