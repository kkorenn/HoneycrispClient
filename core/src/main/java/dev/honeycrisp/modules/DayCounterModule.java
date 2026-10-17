package dev.honeycrisp.modules;

import dev.honeycrisp.module.Anchor;
import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.TextHudModule;
import dev.honeycrisp.platform.GameInfo;

public final class DayCounterModule extends TextHudModule {
	public DayCounterModule() {
		super("daycounter", "Day Counter", "Shows the in-game day number.", Category.HUD, "sun.horizon", Anchor.TOP_LEFT, 4, 130);
	}

	@Override
	protected String text(GameInfo game) {
		return "Day " + (Math.max(0L, game.worldAge()) / 24000L);
	}

	@Override
	protected String previewText() {
		return "Day 42";
	}
}
