package dev.honeycrisp.ui.widgets;

import dev.honeycrisp.platform.Keys;
import dev.honeycrisp.platform.Renderer;
import dev.honeycrisp.ui.draw.Font;

import static dev.honeycrisp.ui.widgets.Theme.*;

/**
 * Text field (3.6), or the search capsule (3.9) when {@link #search}. Caret editing with ←/→, Home/End,
 * ⌥⌫ (word) and ⌘⌫ (line). ponytail: no selection or clipboard (Platform has no clipboard API).
 */
public class TextField extends Widget {
	private static final int MOD_SHIFT = 1, MOD_ALT = 4, MOD_SUPER = 8;
	private final Value<String> value;
	public final boolean search;
	public String placeholder;
	public int maxLength = 256;
	private int caret = -1;
	private long typedAt;
	private final Motion focusAnim = new Motion(Motion.QUICK, 0);

	public TextField(Value<String> value, String placeholder, boolean search) {
		this.value = value;
		this.placeholder = placeholder;
		this.search = search;
	}

	public String text() {
		String s = value.get();
		return s == null ? "" : s;
	}

	public void setText(String s) {
		value.set(s);
		caret = s.length();
	}

	public float prefH() {
		return u(search ? 32 : 28);
	}

	private Font font() {
		return Theme.body;
	}

	private float textX() {
		return x + u(search ? 30 : 8);
	}

	private float textRight() {
		return x + w - u(search && !text().isEmpty() ? 30 : search ? 12 : 8);
	}

	@Override
	public void render(Renderer r, float mx, float my) {
		String s = text();
		if (caret < 0 || caret > s.length()) caret = s.length();
		float f = focusAnim.to(focused);
		float rad = search ? h / 2 : u(8), cy = y + h / 2;
		if (search) {
			Gfx.controlGlass(r, x, y, w, h, rad);
			Gfx.iconAt(r, "magnifyingglass", x + u(10 + 7), cy, u(14), label2);
		} else {
			Gfx.rect(r, x, y, w, h, rad, fill3);
			Gfx.stroke(r, x, y, w, h, rad, hairline, separator);
		}
		if (f > 0.01f) {
			float old = Gfx.opacity;
			Gfx.opacity *= f;
			Gfx.stroke(r, x, y, w, h, rad, u(1), accent);
			Gfx.focusRing(r, x, y, w, h, rad);
			Gfx.opacity = old;
		}
		float tx = textX(), avail = textRight() - tx;
		if (s.isEmpty()) {
			if (placeholder != null) Gfx.textMid(r, Gfx.truncate(placeholder, font(), avail), tx, cy, font(), label3);
		} else {
			float caretX = Gfx.width(s.substring(0, caret), font());
			float shift = Math.max(0, caretX - avail + u(2));
			r.pushClip(tx - u(1), y, avail + u(2), h);
			Gfx.textMid(r, s, tx - shift, cy, font(), label);
			r.popClip();
			if (search) Gfx.iconAt(r, "xmark.circle.fill", x + w - u(10 + 7), cy, u(14), label3);
		}
		if (focused) {
			long t = now - typedAt;
			boolean on = t < 500 || (t % 1060) < 530;
			if (on) {
				float caretX = s.isEmpty() ? 0 : Gfx.width(s.substring(0, caret), font());
				float shift = Math.max(0, caretX - avail + u(2));
				Gfx.fill(r, tx + caretX - shift, cy - u(8), u(1.5f), u(16), accent);
			}
		}
	}

	@Override
	public boolean focusable() {
		return true;
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (search && !text().isEmpty() && mx >= x + w - u(28)) {
			setText("");
			return true;
		}
		String s = text();
		float rel = mx - textX();
		caret = s.length();
		for (int i = 0; i < s.length(); i++) {
			if (Gfx.width(s.substring(0, i + 1), font()) > rel + Gfx.width(s.substring(i, i + 1), font()) / 2) {
				caret = i;
				break;
			}
		}
		typedAt = now;
		return true;
	}

	@Override
	public boolean charTyped(char c) {
		if (c < 32 || c == 127) return false;
		String s = text();
		if (s.length() >= maxLength) return true;
		clampCaret(s);
		value.set(s.substring(0, caret) + c + s.substring(caret));
		caret++;
		typedAt = now;
		return true;
	}

	private void clampCaret(String s) {
		if (caret < 0 || caret > s.length()) caret = s.length();
	}

	@Override
	public boolean keyPressed(int key, int mods) {
		String s = text();
		clampCaret(s);
		typedAt = now;
		switch (key) {
			case Keys.ESCAPE:
				if (search && !s.isEmpty()) setText("");
				else focused = false;
				return true;
			case Keys.ENTER:
			case Keys.TAB:
				focused = false;
				return true;
			case Keys.BACKSPACE: {
				if (caret == 0) return true;
				int from = (mods & MOD_SUPER) != 0 ? 0 : (mods & MOD_ALT) != 0 ? wordStart(s, caret) : caret - 1;
				value.set(s.substring(0, from) + s.substring(caret));
				caret = from;
				return true;
			}
			case Keys.DELETE:
				if (caret < s.length()) value.set(s.substring(0, caret) + s.substring(caret + 1));
				return true;
			case Keys.LEFT:
				caret = (mods & MOD_SUPER) != 0 ? 0 : (mods & MOD_ALT) != 0 ? wordStart(s, caret) : Math.max(0, caret - 1);
				return true;
			case Keys.RIGHT:
				caret = (mods & MOD_SUPER) != 0 ? s.length() : Math.min(s.length(), caret + 1);
				return true;
			case Keys.HOME:
				caret = 0;
				return true;
			case Keys.END:
				caret = s.length();
				return true;
			default:
				// Printable keys arrive through charTyped; swallow them here so screens don't act on them.
				return key >= Keys.SPACE && key < Keys.ESCAPE && (mods & ~MOD_SHIFT) == 0;
		}
	}

	private static int wordStart(String s, int from) {
		int i = from;
		while (i > 0 && s.charAt(i - 1) == ' ') i--;
		while (i > 0 && s.charAt(i - 1) != ' ') i--;
		return i;
	}
}
