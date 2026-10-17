package dev.honeycrisp.modules;

import dev.honeycrisp.module.Anchor;
import dev.honeycrisp.module.BooleanSetting;
import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.TextHudModule;
import dev.honeycrisp.platform.GameInfo;
import dev.honeycrisp.ui.widgets.Theme;

public final class PingModule extends TextHudModule {
	public final BooleanSetting hideInSingleplayer = setting(new BooleanSetting("hideSingleplayer", "Hide in Singleplayer", true));
	public final BooleanSetting colorCode = setting(new BooleanSetting("colorCode", "Color Code", true));
	private int lastPing = -1;

	public PingModule() {
		super("ping", "Ping", "Latency to the server", Category.HUD, "wifi", Anchor.TOP_LEFT, 4, 40);
	}

	@Override
	protected String text(GameInfo game) {
		int p = game.ping();
		lastPing = p;
		if (p < 0) return hideInSingleplayer.on() ? null : "Ping: –";
		return p + " ms";
	}

	@Override
	protected String previewText() {
		lastPing = 42;
		return "42 ms";
	}

	@Override
	protected int color() {
		int p = preview ? 42 : lastPing;
		if (!colorCode.on() || p < 0) return super.color();
		boolean d = Theme.dark;
		return p < 80 ? Theme.green(d) : p < 150 ? Theme.orange(d) : Theme.red(d);
	}
}
