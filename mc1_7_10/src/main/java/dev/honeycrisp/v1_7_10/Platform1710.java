package dev.honeycrisp.v1_7_10;

import dev.honeycrisp.mac.MacNative;
import dev.honeycrisp.platform.GameInfo;
import dev.honeycrisp.platform.Platform;
import dev.honeycrisp.platform.Renderer;
import dev.honeycrisp.ui.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.GuiOpenEvent;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.nio.file.Path;

final class Platform1710 implements Platform {
	private final Renderer renderer = new Render1710();
	private final GameInfo game = new Game1710();
	private double backing;
	/** Set while a GuiOpenEvent is being handled: openScreen then swaps the event's screen instead of nesting. */
	static GuiOpenEvent opening;

	private static Minecraft mc() {
		return Minecraft.getMinecraft();
	}

	static ScaledResolution scaled() {
		return new ScaledResolution(mc(), mc().displayWidth, mc().displayHeight);
	}

	@Override
	public String minecraftVersion() {
		return "1.7.10";
	}

	@Override
	public Renderer renderer() {
		return renderer;
	}

	@Override
	public GameInfo game() {
		return game;
	}

	@Override
	public Path configDir() {
		return new File(mc().mcDataDir, "config/honeycrisp").toPath();
	}

	@Override
	public int scaledWidth() {
		return scaled().getScaledWidth();
	}

	@Override
	public int scaledHeight() {
		return scaled().getScaledHeight();
	}

	@Override
	public double guiScale() {
		return scaled().getScaleFactor();
	}

	@Override
	public double backingScale() {
		if (backing == 0) {
			backing = probeBacking();
		}
		return backing;
	}

	private static double probeBacking() {
		try {
			if (MacNative.available()) {
				return MacNative.backingScale();
			}
		} catch (Throwable ignored) {
		}
		// Reflection on purpose: a direct Display call would be rewritten by Graft (its Win has no such method).
		try {
			float f = ((Number)Class.forName("org.lwjgl.opengl.Display").getMethod("getPixelScaleFactor").invoke(null)).floatValue();
			if (f > 0) {
				return f;
			}
		} catch (Throwable ignored) {
		}
		return 1;
	}

	@Override
	public void openScreen(Screen screen) {
		ScreenWrapper wrapper = new ScreenWrapper(screen);
		if (opening != null) {
			opening.gui = wrapper;
		} else {
			mc().displayGuiScreen(wrapper);
		}
	}

	@Override
	public void closeScreen() {
		mc().displayGuiScreen(null);
	}

	@Override
	public boolean isKeyDown(int glfwKey) {
		int key = KeyMap.toLwjgl(glfwKey);
		return key != 0 && Keyboard.isKeyDown(key);
	}

	@Override
	public String keyName(int glfwKey) {
		int key = KeyMap.toLwjgl(glfwKey);
		return key == 0 ? "" : Keyboard.getKeyName(key);
	}

	@Override
	public long timeMillis() {
		return Minecraft.getSystemTime();
	}
}
