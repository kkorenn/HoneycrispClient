package dev.honeycrisp.ui.screens;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.module.Module;
import dev.honeycrisp.platform.Platform;
import dev.honeycrisp.platform.Renderer;
import dev.honeycrisp.ui.draw.Font;
import dev.honeycrisp.ui.widgets.Button;
import dev.honeycrisp.ui.widgets.Gfx;
import dev.honeycrisp.ui.widgets.Motion;
import dev.honeycrisp.ui.widgets.Toggle;
import dev.honeycrisp.ui.widgets.Ui;
import dev.honeycrisp.ui.widgets.Value;
import dev.honeycrisp.ui.widgets.Widget;

import java.util.List;

import static dev.honeycrisp.ui.widgets.Theme.*;

/** Mod card (3.10): icon well, switch, name, two-line description, gear. Clicking the body toggles the module. */
final class ModCard extends Widget {
	final Module module;
	final Toggle toggle;
	final Button gear;
	private final Motion hover = new Motion(Motion.SNAPPY, 0), well;
	private boolean pressed;

	ModCard(Module module, Runnable openSettings) {
		this.module = module;
		toggle = new Toggle(Value.enabled(module), false);
		gear = Button.icon("gearshape", Button.Style.GLASS, openSettings);
		well = new Motion(Motion.SNAPPY, module.enabled() ? 1 : 0);
	}

	private boolean available() {
		Platform p = Honeycrisp.platform();
		return module.availableOn(p != null ? p.minecraftVersion() : "");
	}

	void render(Renderer r, Ui ui, float mx, float my, String query) {
		boolean avail = available();
		float old = Gfx.opacity;
		if (!avail) Gfx.opacity *= 0.4f;
		ui.add(this);
		float cmx = avail ? mx : -1e6f, cmy = avail ? my : -1e6f;
		float hv = hover.to(avail && hovered(mx, my) ? 1 : 0), on = well.to(avail && module.enabled());
		float g = hv * w * 0.0075f, cx = x - g, cy = y - g, cw = w + 2 * g, ch = h + 2 * g, rad = u(20);
		if (hv > 0.01f) Gfx.shadow(r, cx, cy + u(2 + 4 * hv), cw, ch, rad, u(8 + 8 * hv), alpha(dark ? 0x33000000 : 0x14000000, hv));
		Gfx.groupGlass(r, cx, cy, cw, ch, rad, true);
		if (hv > 0.01f) Gfx.stroke(r, cx, cy, cw, ch, rad, u(1), alpha(strokeSpec, hv));
		if (avail && pressed && hovered(mx, my)) Gfx.rect(r, cx, cy, cw, ch, rad, pressedOverlay());
		// icon well
		float wx = x + u(12), wy = y + u(12), ws = u(36);
		Gfx.rect(r, wx, wy, ws, ws, u(8), mix(fill2, accent, on));
		Gfx.iconAt(r, module.sfSymbol, wx + ws / 2, wy + ws / 2, u(20), mix(label2, onAccent, on));
		if (module.canDisable()) {
			toggle.enabled = true;
			toggle.at(x + w - u(12 + 38), y + u(19), u(38), u(22));
			ui.add(toggle).render(r, cmx, cmy);
			toggle.enabled = avail;
		}
		// name with matched characters emphasized
		float ny = y + u(58);
		Font f = headline;
		String name = Gfx.truncate(module.name, f, w - u(24));
		int at = query.isEmpty() ? -1 : name.toLowerCase().indexOf(query.toLowerCase());
		if (at < 0) {
			Gfx.text(r, name, x + u(12), ny, f, label);
		} else {
			Font b = f.withWeight(Math.min(900, f.weight + 200));
			String a = name.substring(0, at), m = name.substring(at, at + query.length()), z = name.substring(at + query.length());
			float tx = x + u(12);
			Gfx.text(r, a, tx, ny, f, label);
			tx += Gfx.width(a, f);
			Gfx.text(r, m, tx, ny, b, label);
			tx += Gfx.width(m, b);
			Gfx.text(r, z, tx, ny, f, label);
		}
		List<String> lines = Gfx.wrap(module.description, subheadline, w - u(24 + 30), 2);
		float dy = y + u(76), lh = Gfx.lineHeight(subheadline);
		for (String l : lines) {
			Gfx.text(r, l, x + u(12), dy, subheadline, label2);
			dy += lh;
		}
		gear.enabled = true;
		gear.at(x + w - u(12 + 24), y + h - u(12 + 24), u(24), u(24));
		ui.add(gear).render(r, cmx, cmy);
		gear.enabled = avail;
		Gfx.opacity = old;
		if (!avail && hit(mx, my)) {
			Platform p = Honeycrisp.platform();
			ui.tooltip(module.unavailableReason(p != null ? p.minecraftVersion() : ""), mx, my);
		} else if (gear.hovered(mx, my)) {
			ui.tooltip(module.name + " Settings", mx, my);
		}
	}

	@Override
	public void render(Renderer r, float mx, float my) {
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (!available()) return false;
		pressed = button == 0;
		return pressed;
	}

	@Override
	public void mouseReleased(float mx, float my, int button) {
		if (pressed && hit(mx, my) && available() && module.canDisable()) module.toggle();
		pressed = false;
	}
}
