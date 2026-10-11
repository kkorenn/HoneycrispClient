package dev.honeycrisp.ui.draw;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.platform.GameInfo;
import dev.honeycrisp.platform.Platform;
import dev.honeycrisp.platform.Renderer;
import dev.honeycrisp.ui.Screen;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Minimal Platform for headless tests; {@link #install} sets it as Honeycrisp.platform() without init(). */
public final class FakePlatform implements Platform {
	private final Renderer renderer;
	private final int width, height;
	private final double scale;
	/** Optional test hooks: game state, a fixed clock (-1 = real time), opened screens. */
	public GameInfo game;
	public long time = -1;
	public Screen opened;
	public boolean closed;

	public FakePlatform(Renderer renderer, int width, int height, double scale) {
		this.renderer = renderer;
		this.width = width;
		this.height = height;
		this.scale = scale;
	}

	public FakePlatform install() throws ReflectiveOperationException {
		Field f = Honeycrisp.class.getDeclaredField("platform");
		f.setAccessible(true);
		f.set(null, this);
		return this;
	}

	public String minecraftVersion() { return "test"; }
	public Renderer renderer() { return renderer; }
	public GameInfo game() { return game; }
	public Path configDir() { return Paths.get("build", "test-config"); }
	public int scaledWidth() { return width; }
	public int scaledHeight() { return height; }
	public double guiScale() { return scale; }
	public double backingScale() { return 2; }
	public void openScreen(Screen screen) { opened = screen; }
	public void closeScreen() { closed = true; }
	public boolean isKeyDown(int glfwKey) { return false; }
	public String keyName(int glfwKey) {
		if (glfwKey >= 'A' && glfwKey <= 'Z' || glfwKey >= '0' && glfwKey <= '9') return String.valueOf((char) glfwKey);
		switch (glfwKey) {
			case 340: return "Left Shift";
			case 344: return "Right Shift";
			case 341: return "Left Control";
			case 342: return "Left Option";
			case 32: return "Space";
			default: return "Key " + glfwKey;
		}
	}
	public long timeMillis() { return time >= 0 ? time : System.nanoTime() / 1_000_000; }
}
