package dev.honeycrisp.modules;

import dev.honeycrisp.module.Anchor;
import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.TextHudModule;
import dev.honeycrisp.platform.GameInfo;

public final class FpsModule extends TextHudModule {
	public FpsModule() {
		super("fps", "FPS", "Frames per second", Category.HUD, "speedometer", Anchor.TOP_LEFT, 4, 4);
		enabledByDefault();
	}

	@Override
	protected String text(GameInfo game) {
		return game.fps() + " FPS";
	}

	@Override
	protected String previewText() {
		return "240 FPS";
	}
}
