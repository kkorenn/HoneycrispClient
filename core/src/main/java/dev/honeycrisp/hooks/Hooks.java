package dev.honeycrisp.hooks;

/**
 * What version adapters call from their injection points. Every method is cheap and safe to call every frame; the
 * defaults mean "vanilla behavior" so an adapter can wire hooks incrementally.
 */
public interface Hooks {
	void onClientTick();

	/** After the vanilla HUD (GUI-scaled, partial tick). */
	void onRenderHud(float partialTick);

	/** Every key event (GLFW key + action), in and out of screens. Returns true to swallow it. */
	boolean onKey(int glfwKey, int action);

	void onMouseButton(int button, int action);

	/** Player swung/attacked (CPS counters). */
	void onAttack();

	/** Zoom: multiply the FOV by this (1.0 = off). */
	double fovMultiplier(float partialTick);

	/** Whether the zoom should also scale mouse sensitivity (smooth/cinematic zoom). */
	double mouseSensitivityMultiplier();

	boolean freelookActive();

	/** Freelook: mouse movement goes to the free camera instead of the player. Returns true when consumed. */
	boolean consumeMouseLook(double dx, double dy);

	float freelookYaw();

	float freelookPitch();

	boolean oldAnimation(OldAnimation which);

	/** Damage tint (ARGB, alpha = strength); 0 = vanilla red. */
	int hitColor();

	boolean itemPhysics();

	/** Toggle sprint/sneak: adapters OR these into the key states. */
	boolean sprintHeld();

	boolean sneakHeld();

	/** Null = vanilla crosshair. */
	Crosshair crosshair();

	boolean replaceMainMenu();

	/** Called by adapters when the title screen would open; shows the Honeycrisp Client main menu instead. */
	void openMainMenu();

	void openModMenu();

	// ---- Additions (defaults = vanilla, so adapters can adopt them one by one) ----

	/** Preferred over {@link #onKey(int, int)}: screenOpen = any (vanilla or core) screen is shown, e.g. chat. */
	default boolean onKey(int glfwKey, int action, boolean screenOpen) {
		return onKey(glfwKey, action);
	}

	/** Preferred over {@link #onMouseButton(int, int)}: clicks in screens don't count as CPS. */
	default void onMouseButton(int button, int action, boolean screenOpen) {
		onMouseButton(button, action);
	}

	/** Mouse wheel in the world (positive = up/forward). Returns true to swallow it (no hotbar change). */
	default boolean onScroll(double amount) {
		return false;
	}

	/** Freelook wants the third-person camera while active. */
	default boolean freelookThirdPerson() {
		return false;
	}

	/** Fullbright: gamma to use instead of the option; negative = vanilla. */
	default double gammaOverride() {
		return -1;
	}

	/** Time Changer: client-side day time in ticks (0–23999); negative = vanilla. */
	default long fixedDayTime() {
		return -1;
	}

	/** Chat background opacity 0..1; negative = vanilla. */
	default float chatBackgroundOpacity() {
		return -1;
	}

	/** Hide the red score numbers in the sidebar scoreboard. */
	default boolean hideScoreboardNumbers() {
		return false;
	}
}
