package dev.honeycrisp.modules;

import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.Module;
import dev.honeycrisp.module.NumberSetting;

public final class ChatModule extends Module {
	public final NumberSetting backgroundOpacity = setting(new NumberSetting("bgOpacity", "Background Opacity", 0.25, 0, 1, 0.05));

	public ChatModule() {
		super("chat", "Chat", "Chat appearance", Category.VISUAL, "bubble.left");
	}

	public float opacity() {
		return enabled() ? backgroundOpacity.floatValue() : -1;
	}
}
