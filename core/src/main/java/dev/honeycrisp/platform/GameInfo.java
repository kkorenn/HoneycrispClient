package dev.honeycrisp.platform;

import java.util.List;

/** Game state for HUD modules. Adapters return defaults (0, "", empty) when not in a world. */
public interface GameInfo {
	boolean inWorld();

	int fps();

	/** Ping in ms to the current server; -1 in singleplayer / unknown. */
	int ping();

	double x();

	double y();

	double z();

	/** Player yaw in degrees (Minecraft convention: 0 = south). */
	float yaw();

	float pitch();

	String biome();

	/** Server address, or "" in singleplayer. */
	String server();

	/** World time of day in ticks (0–24000). */
	long dayTime();

	String playerName();

	boolean sprinting();

	boolean sneaking();

	/** Held item, or null. */
	ItemInfo heldItem();

	/** Armor from helmet (0) to boots (3); null entries for empty slots. */
	ItemInfo[] armor();

	List<EffectInfo> effects();

	/** An item for HUD display; the adapter can draw its icon (versioned item rendering). */
	interface ItemInfo {
		String name();

		int count();

		/** Remaining durability, -1 when the item has none. */
		int durability();

		int maxDurability();

		/** Draws the vanilla item icon at GUI-scaled (x, y), 16×16. */
		void drawIcon(float x, float y);
	}

	interface EffectInfo {
		String name();

		/** 0-based amplifier (0 = level I). */
		int amplifier();

		int secondsLeft();

		/** True for good effects (speed, strength…). */
		boolean beneficial();

		/** Draws the vanilla effect icon at GUI-scaled (x, y), 18×18. */
		void drawIcon(float x, float y);
	}
}
