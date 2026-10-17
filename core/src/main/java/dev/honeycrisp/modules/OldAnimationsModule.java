package dev.honeycrisp.modules;

import dev.honeycrisp.hooks.OldAnimation;
import dev.honeycrisp.module.BooleanSetting;
import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.Module;

import java.util.Locale;

public final class OldAnimationsModule extends Module {
	private final BooleanSetting[] flags = new BooleanSetting[OldAnimation.values().length];

	public OldAnimationsModule() {
		super("oldanimations", "Old Animations", "1.7-style animations", Category.PVP, "clock.arrow.circlepath");
		for (OldAnimation a : OldAnimation.values()) {
			flags[a.ordinal()] = setting(new BooleanSetting(a.name().toLowerCase(Locale.ROOT), label(a), true));
		}
	}

	public boolean on(OldAnimation which) {
		return enabled() && flags[which.ordinal()].on();
	}

	public BooleanSetting flag(OldAnimation which) {
		return flags[which.ordinal()];
	}

	@Override
	public boolean availableOn(String mcVersion) {
		return !"1.7.10".equals(mcVersion);
	}

	@Override
	public String unavailableReason(String mcVersion) {
		return "1.7 animations are native in Minecraft 1.7.10";
	}

	private static String label(OldAnimation a) {
		switch (a) {
			case BLOCK_HIT: return "Block Hitting";
			case BOW: return "Bow";
			case ROD: return "Fishing Rod";
			case EAT_DRINK: return "Eating & Drinking";
			case SWING: return "Swing";
			case ARMOR_DAMAGE_FLASH: return "Armor Damage Flash";
			case SNEAK: return "Sneaking";
			case HEALTH_FLASH: return "Health Flash";
			default: return a.name();
		}
	}
}
