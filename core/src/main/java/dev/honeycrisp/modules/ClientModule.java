package dev.honeycrisp.modules;

import dev.honeycrisp.module.BooleanSetting;
import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.KeybindSetting;
import dev.honeycrisp.module.Module;
import dev.honeycrisp.platform.Keys;

/** The client's own options; always on. */
public final class ClientModule extends Module {
	public final KeybindSetting menuKey = setting(new KeybindSetting("menuKey", "Mod Menu Key", Keys.RIGHT_SHIFT, KeybindSetting.Mode.HOLD));
	public final BooleanSetting customMainMenu = setting(new BooleanSetting("mainMenu", "Honeycrisp Client Main Menu", true));

	public ClientModule() {
		super("client", "Honeycrisp Client", "General client settings", Category.GENERAL, "gearshape.fill");
		enabledByDefault();
	}

	@Override
	public boolean canDisable() {
		return false;
	}
}
