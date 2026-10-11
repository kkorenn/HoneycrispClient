package dev.honeycrisp.modules;

import dev.honeycrisp.module.BooleanSetting;
import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.Module;

public final class ScoreboardModule extends Module {
	public final BooleanSetting hideNumbers = setting(new BooleanSetting("hideNumbers", "Hide Red Numbers", true));

	public ScoreboardModule() {
		super("scoreboard", "Scoreboard", "Scoreboard tweaks", Category.VISUAL, "list.number");
	}

	public boolean hideNumbers() {
		return enabled() && hideNumbers.on();
	}
}
