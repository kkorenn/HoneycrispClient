package dev.honeycrisp.ui.screens;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.module.BooleanSetting;
import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.ChoiceSetting;
import dev.honeycrisp.module.Config;
import dev.honeycrisp.module.Json;
import dev.honeycrisp.module.Module;
import dev.honeycrisp.module.ModuleRegistry;
import dev.honeycrisp.module.NumberSetting;
import dev.honeycrisp.module.Setting;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/**
 * Appearance preferences (Settings → Appearance). Registered lazily as the always-on "ui" module, so Config saves
 * it with everything else; its saved values are read back from config.json on registration (Config loaded earlier).
 */
public final class UiPrefs extends Module {
	public enum Appearance {
		SYSTEM("System"), DARK("Dark"), LIGHT("Light");

		private final String label;

		Appearance(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public enum Accent {
		SYSTEM, BLUE, PURPLE, PINK, RED, ORANGE, YELLOW, GREEN, GRAPHITE
	}

	public final ChoiceSetting<Appearance> theme = setting(new ChoiceSetting<Appearance>("theme", "Theme", Appearance.SYSTEM));
	public final ChoiceSetting<Accent> accent = setting(new ChoiceSetting<Accent>("accent", "Accent", Accent.SYSTEM));
	public final NumberSetting uiScale = setting(new NumberSetting("uiScale", "UI Scale", 1, 0.75, 1.5, 0.05));
	public final BooleanSetting blur = setting(new BooleanSetting("blur", "Blur Behind Menus", true));
	public final BooleanSetting reduceTransparency = setting(new BooleanSetting("reduceTransparency", "Reduce Transparency", false));
	public final BooleanSetting reduceMotion = setting(new BooleanSetting("reduceMotion", "Reduce Motion", false));

	private static UiPrefs standalone;

	public UiPrefs() {
		super("ui", "Interface", "Menu appearance", Category.GENERAL, "circle.lefthalf.filled");
		enabledByDefault();
	}

	@Override
	public boolean canDisable() {
		return false;
	}

	public static UiPrefs get() {
		ModuleRegistry reg = Honeycrisp.modules();
		if (reg == null) {
			if (standalone == null) standalone = new UiPrefs();
			return standalone;
		}
		UiPrefs p = reg.get(UiPrefs.class);
		if (p == null) {
			p = reg.add(new UiPrefs());
			load(p, Honeycrisp.config());
		}
		return p;
	}

	private static void load(UiPrefs p, Config config) {
		if (config == null || !Files.isRegularFile(config.file())) return;
		try {
			Object root = Json.parse(new String(Files.readAllBytes(config.file()), StandardCharsets.UTF_8));
			Object mods = root instanceof Map ? ((Map<?, ?>) root).get("modules") : null;
			Object o = mods instanceof Map ? ((Map<?, ?>) mods).get(p.id) : null;
			if (!(o instanceof Map)) return;
			for (Setting<?> s : p.settings()) if (((Map<?, ?>) o).containsKey(s.id)) s.fromJson(((Map<?, ?>) o).get(s.id));
		} catch (Exception e) {
			System.err.println("[Honeycrisp Client] ui prefs not loaded: " + e);
		}
	}
}
