package dev.honeycrisp.module;

import dev.honeycrisp.platform.GameInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A feature with an on/off state and settings. Settings are declared as fields via {@link #setting}. */
public abstract class Module {
	public final String id;
	public final String name;
	public final String description;
	public final Category category;
	public final String sfSymbol;
	private final List<Setting<?>> settings = new ArrayList<Setting<?>>();
	private boolean enabled;
	ModuleRegistry registry;

	protected Module(String id, String name, String description, Category category, String sfSymbol) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
		this.sfSymbol = sfSymbol;
	}

	public boolean enabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		if (enabled == this.enabled || (!enabled && !canDisable())) return;
		this.enabled = enabled;
		if (enabled) onEnable();
		else onDisable();
		changed();
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	/** False for always-on modules (the client's own settings); the UI hides their switch. */
	public boolean canDisable() {
		return true;
	}

	public List<Setting<?>> settings() {
		return Collections.unmodifiableList(settings);
	}

	public Setting<?> setting(String id) {
		for (Setting<?> s : settings) if (s.id.equals(id)) return s;
		return null;
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	/** Every client tick while enabled. */
	public void onTick(GameInfo game) {
	}

	/** Enabled state before any config is loaded (no callbacks). */
	protected void enabledByDefault() {
		enabled = true;
	}

	protected <S extends Setting<?>> S setting(S s) {
		s.owner = this;
		settings.add(s);
		return s;
	}

	protected <S extends Setting<?>> S hidden(S s) {
		s.hidden = true;
		return setting(s);
	}

	void changed() {
		if (registry != null) registry.changed();
	}
}
