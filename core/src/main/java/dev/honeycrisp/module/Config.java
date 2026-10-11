package dev.honeycrisp.module;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * config.json: {"version": 1, "modules": {"<id>": {"enabled": bool, "<setting id>": value, ...}}}. Unknown keys are
 * ignored and missing ones keep their defaults, so adding/removing modules or settings needs no migration.
 */
public final class Config {
	public static final int VERSION = 1;
	public static final long SAVE_DELAY_MS = 1000;
	private final Path file;
	private final ModuleRegistry modules;
	private boolean loading;
	private long dirtySince = -1;

	public Config(Path file, ModuleRegistry modules) {
		this.file = file;
		this.modules = modules;
		modules.setChangeListener(new Runnable() {
			@Override
			public void run() {
				markDirty();
			}
		});
	}

	public Path file() {
		return file;
	}

	public void load() {
		if (!Files.isRegularFile(file)) return;
		try {
			fromJson(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
		} catch (IOException | RuntimeException e) {
			System.err.println("[Honeycrisp Client] unreadable config, keeping defaults: " + e);
			try {
				Files.copy(file, file.resolveSibling(file.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
			}
		}
	}

	public void save() {
		dirtySince = -1;
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.write(tmp, toJson().getBytes(StandardCharsets.UTF_8));
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			System.err.println("[Honeycrisp Client] could not save config: " + e);
		}
	}

	public void markDirty() {
		if (!loading && dirtySince < 0) dirtySince = System.currentTimeMillis();
	}

	public boolean dirty() {
		return dirtySince >= 0;
	}

	/** Debounced autosave; call often (client tick). */
	public void tick() {
		if (dirtySince >= 0 && System.currentTimeMillis() - dirtySince >= SAVE_DELAY_MS) save();
	}

	public String toJson() {
		Map<String, Object> mods = new LinkedHashMap<String, Object>();
		for (Module m : modules.all()) {
			Map<String, Object> o = new LinkedHashMap<String, Object>();
			o.put("enabled", m.enabled());
			for (Setting<?> s : m.settings()) o.put(s.id, s.toJson());
			mods.put(m.id, o);
		}
		Map<String, Object> root = new LinkedHashMap<String, Object>();
		root.put("version", VERSION);
		root.put("modules", mods);
		return Json.write(root);
	}

	public void fromJson(String json) {
		Object root = Json.parse(json);
		if (!(root instanceof Map)) throw new IllegalArgumentException("config root is not an object");
		// "version" is kept for future migrations; v1 needs none.
		Object mods = ((Map<?, ?>) root).get("modules");
		if (!(mods instanceof Map)) return;
		loading = true;
		try {
			for (Map.Entry<?, ?> e : ((Map<?, ?>) mods).entrySet()) {
				Module m = modules.byId(String.valueOf(e.getKey()));
				if (m == null || !(e.getValue() instanceof Map)) continue;
				Map<?, ?> o = (Map<?, ?>) e.getValue();
				for (Setting<?> s : m.settings()) if (o.containsKey(s.id)) s.fromJson(o.get(s.id));
				if (o.get("enabled") instanceof Boolean) m.setEnabled((Boolean) o.get("enabled"));
			}
		} finally {
			loading = false;
		}
	}
}
