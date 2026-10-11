package dev.honeycrisp.modules;

import dev.honeycrisp.module.Anchor;
import dev.honeycrisp.module.BooleanSetting;
import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.TextHudModule;
import dev.honeycrisp.platform.GameInfo;

public final class PingModule extends TextHudModule {
	public final BooleanSetting hideInSingleplayer = setting(new BooleanSetting("hideSingleplayer", "Hide in Singleplayer", true));

	public PingModule() {
		super("ping", "Ping", "Latency to the server", Category.HUD, "wifi", Anchor.TOP_LEFT, 4, 40);
	}

	@Override
	protected String text(GameInfo game) {
		int p = game.ping();
		if (p < 0) return hideInSingleplayer.on() ? null : "Ping: –";
		return p + " ms";
	}

	@Override
	protected String previewText() {
		return "42 ms";
	}
}
