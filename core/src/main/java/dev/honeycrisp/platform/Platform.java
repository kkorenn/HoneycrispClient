package dev.honeycrisp.platform;

import java.nio.file.Path;

/** What each version adapter provides to the core. */
public interface Platform {
	String minecraftVersion();

	Renderer renderer();

	GameInfo game();

	Path configDir();

	int scaledWidth();

	int scaledHeight();

	/** GUI-scaled → framebuffer pixels. */
	double guiScale();

	/** Framebuffer pixels per window point (2.0 on Retina). */
	double backingScale();

	/** Shows a core screen (the adapter wraps it in a version Screen that forwards input and rendering). */
	void openScreen(dev.honeycrisp.ui.Screen screen);

	void closeScreen();

	boolean isKeyDown(int glfwKey);

	String keyName(int glfwKey);

	/** Milliseconds, monotonic. */
	long timeMillis();
}
