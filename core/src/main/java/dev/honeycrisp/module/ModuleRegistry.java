package dev.honeycrisp.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** All modules in display order. Get it with {@code Honeycrisp.modules()}. */
public final class ModuleRegistry {
	private final List<Module> modules = new ArrayList<Module>();
	private Runnable changeListener;
	/** Set by the HUD editor while it draws the HUD itself, so onRenderHud doesn't draw it twice. */
	public boolean suppressHud;

	public <M extends Module> M add(M module) {
		if (byId(module.id) != null) throw new IllegalArgumentException("duplicate module id " + module.id);
		module.registry = this;
		modules.add(module);
		return module;
	}

	public List<Module> all() {
		return Collections.unmodifiableList(modules);
	}

	public List<Module> byCategory(Category category) {
		List<Module> out = new ArrayList<Module>();
		for (Module m : modules) if (m.category == category) out.add(m);
		return out;
	}

	public List<HudModule> hud() {
		List<HudModule> out = new ArrayList<HudModule>();
		for (Module m : modules) if (m instanceof HudModule) out.add((HudModule) m);
		return out;
	}

	public Module byId(String id) {
		for (Module m : modules) if (m.id.equals(id)) return m;
		return null;
	}

	public <M extends Module> M get(Class<M> type) {
		for (Module m : modules) if (m.getClass() == type) return type.cast(m);
		return null;
	}

	/** Called on any enable/setting change (the config uses it to autosave). */
	public void setChangeListener(Runnable listener) {
		this.changeListener = listener;
	}

	void changed() {
		if (changeListener != null) changeListener.run();
	}
}
