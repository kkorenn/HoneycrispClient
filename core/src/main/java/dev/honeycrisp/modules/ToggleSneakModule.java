package dev.honeycrisp.modules;

import dev.honeycrisp.platform.Keys;

public final class ToggleSneakModule extends ToggleSprintModule {
	public ToggleSneakModule() {
		super("togglesneak", "Toggle Sneak", "Sneak stays on after pressing the sneak key", "figure.walk",
				Keys.LEFT_SHIFT, "Sneaking", true, 18);
	}
}
