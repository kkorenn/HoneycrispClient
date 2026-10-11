package dev.honeycrisp.ui.widgets;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.module.KeybindSetting;
import dev.honeycrisp.module.Module;
import dev.honeycrisp.module.Setting;
import dev.honeycrisp.platform.Keys;
import dev.honeycrisp.platform.Platform;
import dev.honeycrisp.platform.Renderer;

import static dev.honeycrisp.ui.widgets.Theme.*;

/**
 * Key-bind button (3.7). Click to listen; the next key binds it, Esc cancels, ⌫ unbinds. The legend turns orange
 * when another binding uses the same key. ponytail: keyboard keys only (KeybindSetting stores GLFW keys, no mouse).
 */
public class KeybindButton extends Widget {
	private final KeybindSetting s;

	public KeybindButton(KeybindSetting s) {
		this.s = s;
	}

	public static String keyName(int key) {
		if (key == Keys.UNKNOWN) return "None";
		Platform p = Honeycrisp.platform();
		String n = p != null ? p.keyName(key) : null;
		return n == null || n.isEmpty() ? "Key " + key : n;
	}

	private String legend() {
		return focused ? "Press a key…" : keyName(s.key());
	}

	public float prefW() {
		return Math.max(u(64), Gfx.width(legend(), Theme.caption2.withWeight(600)) + u(20));
	}

	public float prefH() {
		return u(24);
	}

	public KeybindButton place(float right, float cy) {
		at(right - prefW(), cy - prefH() / 2, prefW(), prefH());
		return this;
	}

	/** The other setting bound to the same key, or null. */
	public KeybindSetting conflict() {
		if (s.key() == Keys.UNKNOWN || Honeycrisp.modules() == null) return null;
		for (Module m : Honeycrisp.modules().all())
			for (Setting<?> o : m.settings())
				if (o != s && o instanceof KeybindSetting && ((KeybindSetting) o).key() == s.key()) return (KeybindSetting) o;
		return null;
	}

	@Override
	public void render(Renderer r, float mx, float my) {
		float rad = u(6);
		Gfx.rect(r, x, y, w, h, rad, fill2);
		r.pushClip(x, y + h - u(3), w, u(3));
		Gfx.stroke(r, x, y, w, h, rad, u(1), 0x33000000);
		r.popClip();
		if (hovered(mx, my)) Gfx.rect(r, x, y, w, h, rad, hoverOverlay());
		int fg = focused ? label2 : conflict() != null ? orange : label;
		if (focused) {
			float pulse = 0.7f + 0.3f * (float) Math.sin(now / 1000.0 * 2 * Math.PI);
			Gfx.stroke(r, x, y, w, h, rad, u(1.5f), alpha(accent, pulse));
		}
		Gfx.textMidCentered(r, legend(), x + w / 2, y + h / 2, Theme.caption2.withWeight(600), fg);
	}

	@Override
	public boolean focusable() {
		return true;
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		return button == 0;
	}

	@Override
	public boolean keyPressed(int key, int mods) {
		if (key == Keys.ESCAPE) {
			focused = false;
			return true;
		}
		s.set(key == Keys.BACKSPACE ? Keys.UNKNOWN : key);
		focused = false;
		return true;
	}

	@Override
	public boolean charTyped(char c) {
		return true;
	}
}
