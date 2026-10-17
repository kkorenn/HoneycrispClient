package dev.honeycrisp.modules;

import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.ColorSetting;
import dev.honeycrisp.module.Module;

public final class HitColorModule extends Module {
	public final ColorSetting color = setting(new ColorSetting("color", "Color", 0x66FF0000));

	public HitColorModule() {
		super("hitcolor", "Hit Color", "Custom damage tint", Category.PVP, "burst");
	}

	public int argb() {
		return enabled() ? color.argb() : 0;
	}
}
