package dev.honeycrisp;

import dev.honeycrisp.hooks.Hooks;
import dev.honeycrisp.hooks.HooksImpl;
import dev.honeycrisp.module.Config;
import dev.honeycrisp.module.ModuleRegistry;
import dev.honeycrisp.modules.Features;
import dev.honeycrisp.platform.Platform;
import dev.honeycrisp.ui.Screen;

/** Entry point. Adapters call {@link #init} once the game window and renderer exist, then use {@link #hooks()}. */
public final class Honeycrisp {
	public static final String NAME = "Honeycrisp Client";
	private static final Screens SCREENS = new Screens();
	private static Platform platform;
	private static Hooks hooks;
	private static ModuleRegistry modules;
	private static Config config;

	private Honeycrisp() {
	}

	public static void init(Platform platform) {
		Honeycrisp.platform = platform;
		Honeycrisp.hooks = createHooks(platform);
	}

	public static Platform platform() {
		return platform;
	}

	public static Hooks hooks() {
		return hooks;
	}

	public static ModuleRegistry modules() {
		return modules;
	}

	public static Config config() {
		return config;
	}

	/** Screen factories; defaults are the conventional ui.screens classes when present, the UI may replace them. */
	public static Screens screens() {
		return SCREENS;
	}

	public static boolean initialized() {
		return platform != null;
	}

	private static Hooks createHooks(Platform platform) {
		modules = Features.createRegistry();
		final Config c = config = new Config(platform.configDir().resolve("honeycrisp").resolve("config.json"), modules);
		c.load();
		Runtime.getRuntime().addShutdownHook(new Thread("Honeycrisp Client config save") {
			@Override
			public void run() {
				if (c.dirty()) c.save();
			}
		});
		return new HooksImpl(platform, modules, c);
	}

	public interface ScreenFactory {
		Screen create();
	}

	public static final class Screens {
		public ScreenFactory modMenu = byName("dev.honeycrisp.ui.screens.ModMenu");
		public ScreenFactory hudEditor = byName("dev.honeycrisp.ui.screens.HudEditor");
		public ScreenFactory mainMenu = byName("dev.honeycrisp.ui.screens.MainMenu");

		/** A factory for a public no-arg Screen subclass, or null when the class isn't there. */
		public static ScreenFactory byName(String className) {
			final Class<?> cls;
			try {
				cls = Class.forName(className, false, Honeycrisp.class.getClassLoader());
			} catch (ClassNotFoundException | LinkageError e) {
				return null;
			}
			if (!Screen.class.isAssignableFrom(cls)) return null;
			return new ScreenFactory() {
				@Override
				public Screen create() {
					try {
						return (Screen) cls.getConstructor().newInstance();
					} catch (ReflectiveOperationException e) {
						throw new IllegalStateException("cannot create " + cls.getName(), e);
					}
				}
			};
		}
	}
}
