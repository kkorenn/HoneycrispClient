package dev.honeycrisp.modules;

import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.Module;
import dev.honeycrisp.module.NumberSetting;

/** Client-side fixed time of day. */
public final class TimeChangerModule extends Module {
	public final NumberSetting time = setting(new NumberSetting("time", "Time (ticks)", 6000, 0, 23999, 100));

	public TimeChangerModule() {
		super("timechanger", "Time Changer", "Fixed time of day, only for you", Category.VISUAL, "moon.stars");
	}

	public long fixedTime() {
		return enabled() ? (long) time.value() : -1;
	}
}
