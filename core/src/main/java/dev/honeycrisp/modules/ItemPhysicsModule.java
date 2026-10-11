package dev.honeycrisp.modules;

import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.Module;

public final class ItemPhysicsModule extends Module {
	public ItemPhysicsModule() {
		super("itemphysics", "Item Physics", "Dropped items lie flat on the ground", Category.VISUAL, "cube.transparent");
	}
}
