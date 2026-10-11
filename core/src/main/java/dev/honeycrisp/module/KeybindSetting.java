package dev.honeycrisp.module;

import dev.honeycrisp.platform.Keys;

import java.util.LinkedHashMap;
import java.util.Map;

/** A GLFW key (Keys.UNKNOWN = unbound) with hold or toggle activation. Fed by HooksImpl from Hooks.onKey. */
public class KeybindSetting extends Setting<Integer> {
	public enum Mode {
		HOLD("Hold"), TOGGLE("Toggle");

		private final String label;

		Mode(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final Mode defaultMode;
	private Mode mode;
	private boolean down, toggled;

	public KeybindSetting(String id, String name, int defaultKey, Mode defaultMode) {
		super(id, name, defaultKey);
		this.defaultMode = defaultMode;
		this.mode = defaultMode;
	}

	public int key() {
		return value;
	}

	public Mode mode() {
		return mode;
	}

	public void setMode(Mode mode) {
		if (mode == null || mode == this.mode) return;
		this.mode = mode;
		toggled = false;
		changed();
	}

	@Override
	public void set(Integer v) {
		release();
		super.set(v);
	}

	@Override
	public void reset() {
		setMode(defaultMode);
		super.reset();
	}

	/** Returns true when the event was for this key. Only PRESS edges flip the toggle (key repeat is ignored). */
	public boolean handle(int key, int action) {
		if (key != value || key == Keys.UNKNOWN) return false;
		if (action == Keys.PRESS) {
			if (!down) toggled = !toggled;
			down = true;
		} else if (action == Keys.RELEASE) {
			down = false;
		}
		return true;
	}

	public boolean active() {
		return mode == Mode.HOLD ? down : toggled;
	}

	public boolean down() {
		return down;
	}

	/** Forget the physical/toggled state (focus loss, rebinding). */
	public void release() {
		down = false;
		toggled = false;
	}

	@Override
	public Object toJson() {
		Map<String, Object> m = new LinkedHashMap<String, Object>();
		m.put("key", value);
		m.put("mode", mode.name());
		return m;
	}

	@Override
	public void fromJson(Object json) {
		if (json instanceof Number) set(((Number) json).intValue());
		if (!(json instanceof Map)) return;
		Map<?, ?> m = (Map<?, ?>) json;
		if (m.get("key") instanceof Number) set(((Number) m.get("key")).intValue());
		Object md = m.get("mode");
		for (Mode x : Mode.values()) if (x.name().equals(md)) setMode(x);
	}
}
