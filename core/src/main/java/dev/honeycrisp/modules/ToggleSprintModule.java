package dev.honeycrisp.modules;

import dev.honeycrisp.module.Anchor;
import dev.honeycrisp.module.BooleanSetting;
import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.KeybindSetting;
import dev.honeycrisp.module.TextHudModule;
import dev.honeycrisp.platform.GameInfo;
import dev.honeycrisp.platform.Keys;

/** Toggle Sprint / Toggle Sneak share this: a toggle key the adapter ORs into the vanilla key, plus a HUD label. */
public class ToggleSprintModule extends TextHudModule {
	public final KeybindSetting key;
	public final BooleanSetting showText = setting(new BooleanSetting("showText", "Show HUD Text", true));
	private final String verb;
	private final boolean sneak;

	public ToggleSprintModule() {
		this("togglesprint", "Toggle Sprint", "Sprint stays on after pressing the sprint key", "figure.run",
				Keys.LEFT_CONTROL, "Sprinting", false, 4);
	}

	ToggleSprintModule(String id, String name, String desc, String symbol, int defaultKey, String verb, boolean sneak, float y) {
		super(id, name, desc, Category.PVP, symbol, Anchor.BOTTOM_LEFT, 4, -y);
		this.key = setting(new KeybindSetting("key", "Key", defaultKey, KeybindSetting.Mode.TOGGLE));
		this.verb = verb;
		this.sneak = sneak;
	}

	/** Adapter ORs this into the vanilla key state. */
	public boolean held() {
		return enabled() && key.active();
	}

	@Override
	protected void onDisable() {
		key.release();
	}

	@Override
	protected String text(GameInfo game) {
		if (!showText.on()) return null;
		if (held()) return "[" + verb + " (Toggled)]";
		if (sneak ? game.sneaking() : game.sprinting()) return "[" + verb + " (Vanilla)]";
		return null;
	}

	@Override
	protected String previewText() {
		return showText.on() ? "[" + verb + " (Toggled)]" : null;
	}
}
