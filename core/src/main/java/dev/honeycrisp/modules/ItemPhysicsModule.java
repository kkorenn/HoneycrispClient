package dev.honeycrisp.modules;

import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.Module;

public final class ItemPhysicsModule extends Module {
	public ItemPhysicsModule() {
		super("itemphysics", "Item Physics", "Dropped items lie flat on the ground", Category.VISUAL, "cube");
	}

	@Override
	public boolean availableOn(String mcVersion) {
		return "1.8.9".equals(mcVersion);
	}

	@Override
	public String unavailableReason(String mcVersion) {
		return "Item Physics is not available on Minecraft " + mcVersion + " yet";
	}
}
