package dev.honeycrisp.module;

public class TextSetting extends Setting<String> {
	public final int maxLength;

	public TextSetting(String id, String name, String defaultValue, int maxLength) {
		super(id, name, defaultValue);
		this.maxLength = maxLength;
	}

	@Override
	protected String sanitize(String v) {
		return v == null || v.length() <= maxLength ? v : v.substring(0, maxLength);
	}

	@Override
	public Object toJson() {
		return value;
	}

	@Override
	public void fromJson(Object json) {
		if (json instanceof String) set((String) json);
	}
}
