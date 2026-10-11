package dev.honeycrisp.module;

/** One of an enum's constants; the UI shows {@code toString()} of each constant. */
public class ChoiceSetting<E extends Enum<E>> extends Setting<E> {
	private final E[] choices;

	public ChoiceSetting(String id, String name, E defaultValue) {
		super(id, name, defaultValue);
		this.choices = defaultValue.getDeclaringClass().getEnumConstants();
	}

	public E[] choices() {
		return choices.clone();
	}

	public void cycle(int direction) {
		int n = choices.length;
		set(choices[((value.ordinal() + direction) % n + n) % n]);
	}

	@Override
	public Object toJson() {
		return value.name();
	}

	@Override
	public void fromJson(Object json) {
		if (!(json instanceof String)) return;
		for (E e : choices) if (e.name().equalsIgnoreCase((String) json)) set(e);
	}
}
