package dev.honeycrisp.module;

public class BooleanSetting extends Setting<Boolean> {
	public BooleanSetting(String id, String name, boolean defaultValue) {
		super(id, name, defaultValue);
	}

	public boolean on() {
		return value;
	}

	public void toggle() {
		set(!value);
	}

	@Override
	public Object toJson() {
		return value;
	}

	@Override
	public void fromJson(Object json) {
		if (json instanceof Boolean) set((Boolean) json);
	}
}
