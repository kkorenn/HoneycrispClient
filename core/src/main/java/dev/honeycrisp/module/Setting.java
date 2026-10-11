package dev.honeycrisp.module;

/** A module option. UI code switches on the subclass (BooleanSetting, NumberSetting, ...). */
public abstract class Setting<T> {
	public final String id;
	public final String name;
	protected final T defaultValue;
	protected T value;
	String description = "";
	boolean hidden;
	Module owner;

	protected Setting(String id, String name, T defaultValue) {
		this.id = id;
		this.name = name;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	public T get() {
		return value;
	}

	public void set(T v) {
		v = sanitize(v);
		if (v == null || v.equals(value)) return;
		value = v;
		changed();
	}

	public T defaultValue() {
		return defaultValue;
	}

	public void reset() {
		set(defaultValue);
	}

	public String description() {
		return description;
	}

	/** Hidden settings (HUD position) are saved but not shown in the mod menu. */
	public boolean hidden() {
		return hidden;
	}

	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S describe(String description) {
		this.description = description;
		return (S) this;
	}

	protected T sanitize(T v) {
		return v;
	}

	protected void changed() {
		if (owner != null) owner.changed();
	}

	/** JSON value (Boolean/Double/String/Map/List) for the config. */
	public abstract Object toJson();

	/** Must tolerate any JSON shape: wrong types are ignored. */
	public abstract void fromJson(Object json);
}
