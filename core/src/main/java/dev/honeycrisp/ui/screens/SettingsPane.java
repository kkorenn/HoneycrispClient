package dev.honeycrisp.ui.screens;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.module.KeybindSetting;
import dev.honeycrisp.module.Module;
import dev.honeycrisp.module.Setting;
import dev.honeycrisp.modules.ClientModule;
import dev.honeycrisp.platform.Platform;
import dev.honeycrisp.platform.Renderer;
import dev.honeycrisp.ui.widgets.Form;
import dev.honeycrisp.ui.widgets.Gfx;
import dev.honeycrisp.ui.widgets.KeybindButton;
import dev.honeycrisp.ui.widgets.Segmented;
import dev.honeycrisp.ui.widgets.Slider;
import dev.honeycrisp.ui.widgets.Theme;
import dev.honeycrisp.ui.widgets.Toggle;
import dev.honeycrisp.ui.widgets.Ui;
import dev.honeycrisp.ui.widgets.Value;
import dev.honeycrisp.ui.widgets.Widget;

import static dev.honeycrisp.ui.widgets.Theme.*;

/** Settings (DESIGN 4.5) in the mod-menu content area: General, Appearance, HUD, Keybinds, About. */
final class SettingsPane {
	private final Ui ui;
	private Form form;

	SettingsPane(Ui ui) {
		this.ui = ui;
	}

	private Form form() {
		if (form != null) return form;
		form = new Form();
		UiPrefs prefs = UiPrefs.get();
		ClientModule client = Honeycrisp.modules() != null ? Honeycrisp.modules().get(ClientModule.class) : null;
		if (client != null) {
			Form.Group g = form.group("General");
			g.row("Open Mod Menu", new KeybindButton(client.menuKey));
			g.row("Replace Main Menu", new Toggle(Value.of(client.customMainMenu), true));
		}
		Form.Group a = form.group("Appearance");
		a.row("Theme", Segmented.of(prefs.theme));
		a.row("Accent", new AccentPicker(prefs));
		Slider scale = new Slider(prefs.uiScale);
		scale.percent = true;
		a.row("UI Scale", scale);
		a.row(prefs.blur.name, new Toggle(Value.of(prefs.blur), true));
		a.row(prefs.reduceTransparency.name, new Toggle(Value.of(prefs.reduceTransparency), true));
		a.row(prefs.reduceMotion.name, new Toggle(Value.of(prefs.reduceMotion), true));
		Form.Group h = form.group("HUD");
		h.link("Edit HUD Layout", new Runnable() {
			public void run() {
				ModMenu.openHudEditor();
			}
		}).glyph("rectangle.3.group");
		Form.Group k = form.group("Keybinds");
		for (Module m : ModMenu.mods())
			for (Setting<?> s : m.settings())
				if (s instanceof KeybindSetting && !s.hidden()) k.row(m.name, new KeybindButton((KeybindSetting) s)).detail(s.name);
		Form.Group about = form.group("About");
		Platform p = Honeycrisp.platform();
		about.value("Version", BaseScreen.clientVersion());
		about.value("Minecraft", p != null ? p.minecraftVersion() : "");
		about.value("Renderer", "Metal via Graft");
		return form;
	}

	float height(float w) {
		return form().height(w);
	}

	void render(Renderer r, float x, float y, float w, float mx, float my) {
		form().render(r, ui, x, y, w, mx, my);
	}

	/** Row of 9 accent swatches (20u): System (multicolor) then the presets; selected gets a ring and checkmark. */
	static final class AccentPicker extends Widget {
		private final UiPrefs prefs;

		AccentPicker(UiPrefs prefs) {
			this.prefs = prefs;
		}

		@Override
		public float prefW() {
			return UiPrefs.Accent.values().length * u(20 + 8) - u(8);
		}

		@Override
		public float prefH() {
			return u(20);
		}

		@Override
		public void render(Renderer r, float mx, float my) {
			UiPrefs.Accent[] all = UiPrefs.Accent.values();
			float d = u(20);
			for (int i = 0; i < all.length; i++) {
				float sx = x + i * (d + u(8));
				if (all[i] == UiPrefs.Accent.SYSTEM) {
					// multicolor wheel from 4 quarters
					int[] q = {Theme.red, Theme.yellow, Theme.green, Theme.blue};
					for (int j = 0; j < 4; j++) {
						r.pushClip(sx + (j % 2) * d / 2, y + (j / 2) * d / 2, d / 2, d / 2);
						Gfx.capsule(r, sx, y, d, d, q[j == 2 ? 3 : j == 3 ? 2 : j]);
						r.popClip();
					}
				} else {
					Gfx.capsule(r, sx, y, d, d, 0xFF000000 | Theme.resolveAccent(all[i]));
				}
				Gfx.stroke(r, sx, y, d, d, d / 2, hairline, 0x26000000);
				if (prefs.accent.get() == all[i]) {
					Gfx.stroke(r, sx - u(3), y - u(3), d + u(6), d + u(6), d / 2 + u(3), u(2), label2);
					Gfx.iconAt(r, "checkmark", sx + d / 2, y + d / 2, u(10), 0xFFFFFFFF);
				}
			}
		}

		@Override
		public boolean mouseClicked(float mx, float my, int button) {
			int i = (int) ((mx - x + u(4)) / u(28));
			UiPrefs.Accent[] all = UiPrefs.Accent.values();
			if (i >= 0 && i < all.length) prefs.accent.set(all[i]);
			return true;
		}
	}
}
