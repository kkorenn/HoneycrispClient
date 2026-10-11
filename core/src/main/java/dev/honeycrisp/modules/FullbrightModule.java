package dev.honeycrisp.modules;

import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.Module;
import dev.honeycrisp.module.NumberSetting;

public final class FullbrightModule extends Module {
	public final NumberSetting gamma = setting(new NumberSetting("gamma", "Gamma", 10, 1, 15, 0.5));

	public FullbrightModule() {
		super("fullbright", "Fullbright", "See in the dark", Category.VISUAL, "sun.max");
	}

	public double gammaOverride() {
		return enabled() ? gamma.value() : -1;
	}
}
