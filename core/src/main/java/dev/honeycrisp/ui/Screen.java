package dev.honeycrisp.ui;

import dev.honeycrisp.platform.Renderer;

/**
 * A full-screen UI of the core (main menu, mod menu, HUD editor). Adapters wrap it in their version's Screen and
 * forward these calls in GUI-scaled coordinates. Return true from input handlers when consumed.
 */
public abstract class Screen {
	protected int width;
	protected int height;

	/** Called when shown and on every resize. */
	public void init(int width, int height) {
		this.width = width;
		this.height = height;
	}

	public abstract void render(Renderer renderer, float mouseX, float mouseY, float partialTick);

	public boolean mouseClicked(float x, float y, int button) {
		return false;
	}

	public boolean mouseReleased(float x, float y, int button) {
		return false;
	}

	public boolean mouseDragged(float x, float y, int button, float dx, float dy) {
		return false;
	}

	public boolean mouseScrolled(float x, float y, float amount) {
		return false;
	}

	/** GLFW key code, GLFW modifier bits. Escape closes by default (adapter behavior). */
	public boolean keyPressed(int key, int modifiers) {
		return false;
	}

	public boolean charTyped(char c) {
		return false;
	}

	public boolean pausesGame() {
		return false;
	}

	/** Draw the world (or vanilla panorama) behind this screen instead of an opaque background. */
	public boolean transparentBackground() {
		return true;
	}

	public void onClose() {
	}
}
