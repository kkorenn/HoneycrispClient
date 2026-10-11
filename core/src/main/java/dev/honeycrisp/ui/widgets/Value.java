package dev.honeycrisp.ui.widgets;

import dev.honeycrisp.module.ChoiceSetting;
import dev.honeycrisp.module.Module;
import dev.honeycrisp.module.Setting;

/** What a control edits. Adapters for settings and module state. */
public interface Value<T> {
	T get();

	void set(T v);

	static <T> Value<T> of(final Setting<T> s) {
		return new Value<T>() {
			public T get() {
				return s.get();
			}

			public void set(T v) {
				s.set(v);
			}
		};
	}

	static Value<Boolean> enabled(final Module m) {
		return new Value<Boolean>() {
			public Boolean get() {
				return m.enabled();
			}

			public void set(Boolean v) {
				m.setEnabled(v);
			}
		};
	}

	/** A choice as its ordinal (segmented controls, pop-ups). */
	static <E extends Enum<E>> Value<Integer> index(final ChoiceSetting<E> s) {
		return new Value<Integer>() {
			public Integer get() {
				return s.get().ordinal();
			}

			public void set(Integer v) {
				s.set(s.choices()[v]);
			}
		};
	}

	/** Plain holder (UI-only state). */
	static <T> Value<T> local(final T initial) {
		return new Value<T>() {
			T v = initial;

			public T get() {
				return v;
			}

			public void set(T value) {
				v = value;
			}
		};
	}
}
