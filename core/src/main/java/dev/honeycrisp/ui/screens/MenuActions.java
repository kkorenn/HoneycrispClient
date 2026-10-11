package dev.honeycrisp.ui.screens;

/**
 * Vanilla actions the Honeycrisp Client main menu needs from the version adapter. Adapters register one at startup with
 * {@link #set}; until then the main menu hides the buttons that need it (Singleplayer, Multiplayer, Settings,
 * Language, Quit) and shows only Mods and Cosmetics. All calls happen on the render thread from a click.
 */
public interface MenuActions {
	/** Opens vanilla's world selection screen. */
	void singleplayer();

	/** Opens vanilla's server list. */
	void multiplayer();

	/** Opens vanilla's options screen. */
	void options();

	/** Opens vanilla's language screen. */
	void language();

	/** Quits the game. */
	void quit();

	/** Vanilla's copyright line (kept as the version shows it). */
	default String copyright() {
		return "Copyright Mojang AB. Do not distribute!";
	}

	final class Holder {
		static MenuActions actions;

		private Holder() {
		}
	}

	static void set(MenuActions actions) {
		Holder.actions = actions;
	}

	/** The registered actions, or null. */
	static MenuActions get() {
		return Holder.actions;
	}
}
