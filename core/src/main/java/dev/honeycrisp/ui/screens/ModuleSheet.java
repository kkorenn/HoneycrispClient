package dev.honeycrisp.ui.screens;

import dev.honeycrisp.module.HudModule;
import dev.honeycrisp.module.Module;
import dev.honeycrisp.module.Setting;
import dev.honeycrisp.platform.Renderer;
import dev.honeycrisp.ui.widgets.Button;
import dev.honeycrisp.ui.widgets.Form;
import dev.honeycrisp.ui.widgets.Sheet;
import dev.honeycrisp.ui.widgets.Toggle;
import dev.honeycrisp.ui.widgets.Ui;
import dev.honeycrisp.ui.widgets.Value;

import java.util.Arrays;
import java.util.List;

import static dev.honeycrisp.ui.widgets.Theme.*;

/** Per-mod settings sheet (4.2): grouped controls for every visible setting; HUD modules get "Appearance" first. */
final class ModuleSheet extends Sheet {
	private Module module;
	private Form form;
	private Toggle enable;
	private final Button reset = new Button("Reset to Defaults", null, Button.Size.REGULAR, Button.Style.PLAIN_DESTRUCTIVE, new Runnable() {
		public void run() {
			for (Setting<?> s : module.settings()) if (!s.hidden()) s.reset();
		}
	});
	private final Button done = new Button("Done", null, Button.Size.REGULAR, Button.Style.PRIMARY, new Runnable() {
		public void run() {
			close();
		}
	});

	ModuleSheet(Ui ui) {
		super(ui);
	}

	Module module() {
		return module;
	}

	void open(Module m) {
		module = m;
		enable = m.canDisable() ? new Toggle(Value.enabled(m), false) : null;
		form = new Form();
		List<Setting<?>> appearance = m instanceof HudModule
			? Arrays.<Setting<?>>asList(((HudModule) m).style, ((HudModule) m).scale, ((HudModule) m).background, ((HudModule) m).outline, ((HudModule) m).shadow, ((HudModule) m).textColor)
			: Arrays.<Setting<?>>asList();
		if (m instanceof HudModule) {
			Form.Group g = form.group("Appearance");
			for (Setting<?> s : appearance) form.add(g, ui, s);
			g.link("Edit Position…", new Runnable() {
				public void run() {
					close();
					ModMenu.openHudEditor();
				}
			});
		}
		Form.Group opts = null;
		for (Setting<?> s : m.settings()) {
			if (s.hidden() || appearance.contains(s)) continue;
			if (opts == null) opts = form.group(m instanceof HudModule ? "Options" : null);
			form.add(opts, ui, s);
		}
		if (form.groups.isEmpty()) form.group(null).row("This mod has no settings.");
		open();
	}

	@Override
	protected String title() {
		return module.name;
	}

	@Override
	protected String glyph() {
		return module.sfSymbol;
	}

	@Override
	protected boolean glyphActive() {
		return module.enabled();
	}

	@Override
	protected float bodyHeight(float width) {
		return form.height(width);
	}

	@Override
	protected void renderBody(Renderer r, float x, float y, float width, float mx, float my) {
		form.render(r, ui, x, y, width, mx, my);
	}

	@Override
	protected float renderAccessory(Renderer r, float right, float cy, float mx, float my) {
		if (enable == null) return 0;
		enable.place(right, cy);
		ui.add(enable).render(r, mx, my);
		return enable.w + u(12);
	}

	@Override
	protected void renderFooter(Renderer r, float left, float right, float cy, float mx, float my) {
		reset.at(left - u(8), cy - u(14), reset.prefW(), u(28));
		ui.add(reset).render(r, mx, my);
		done.at(right - Math.max(u(80), done.prefW()), cy - u(14), Math.max(u(80), done.prefW()), u(28));
		ui.add(done).render(r, mx, my);
	}
}
