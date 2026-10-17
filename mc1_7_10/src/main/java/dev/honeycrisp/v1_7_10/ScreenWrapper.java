package dev.honeycrisp.v1_7_10;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.platform.Keys;
import dev.honeycrisp.ui.Screen;
import dev.honeycrisp.ui.screens.MainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.entity.RenderItem;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Forwards a vanilla GuiScreen's rendering and input to a core Screen, in float GUI coordinates. */
final class ScreenWrapper extends GuiScreen {
	final Screen screen;
	private final Panorama1710 panorama;
	private float lastX, lastY;

	ScreenWrapper(Screen screen) {
		this.screen = screen;
		panorama = screen instanceof MainMenu && Panorama1710.available() ? new Panorama1710() : null;
	}

	/** GuiScreen's shared RenderItem (protected), for HUD item icons. */
	static RenderItem items() {
		return itemRender;
	}

	private float mouseX() {
		return Mouse.getEventX() * width / (float)mc.displayWidth;
	}

	private float mouseY() {
		return height - Mouse.getEventY() * height / (float)mc.displayHeight - 1;
	}

	@Override
	public void initGui() {
		if (panorama != null) panorama.setWorldAndResolution(mc, width, height);
		Keyboard.enableRepeatEvents(true);
		screen.init(width, height);
	}

	@Override
	public void updateScreen() {
		if (panorama != null) panorama.updateScreen();
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		if (panorama != null && mc.theWorld == null) {
			panorama.drawScreen(mouseX, mouseY, partialTicks);
		} else if (!screen.transparentBackground() || mc.theWorld == null) {
			drawDefaultBackground();
		}
		float x = Mouse.getX() * width / (float)mc.displayWidth;
		float y = height - Mouse.getY() * height / (float)mc.displayHeight - 1;
		screen.render(Honeycrisp.platform().renderer(), x, y, partialTicks);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		lastX = mouseX();
		lastY = mouseY();
		screen.mouseClicked(lastX, lastY, button);
	}

	/** 1.7.10's mouseReleased; button -1 is a plain move. */
	@Override
	protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
		if (button >= 0) {
			screen.mouseReleased(mouseX(), mouseY(), button);
		}
	}

	@Override
	protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
		float x = mouseX(), y = mouseY();
		screen.mouseDragged(x, y, button, x - lastX, y - lastY);
		lastX = x;
		lastY = y;
	}

	@Override
	public void handleMouseInput() {
		super.handleMouseInput();
		int wheel = Mouse.getEventDWheel();
		if (wheel != 0) {
			screen.mouseScrolled(mouseX(), mouseY(), Math.signum(wheel));
		}
	}

	@Override
	protected void keyTyped(char c, int keyCode) {
		int key = KeyMap.toGlfw(keyCode);
		if (key != Keys.UNKNOWN && screen.keyPressed(key, modifiers())) {
			return;
		}
		if (c >= ' ' && c != 127 && screen.charTyped(c)) {
			return;
		}
		if (keyCode == Keyboard.KEY_ESCAPE) {
			mc.displayGuiScreen(null);
		}
	}

	private static int modifiers() {
		int m = 0;
		if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
			m |= 1;
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
			m |= 2;
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)) {
			m |= 4;
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_LMETA) || Keyboard.isKeyDown(Keyboard.KEY_RMETA)) {
			m |= 8;
		}
		return m;
	}

	@Override
	public void onGuiClosed() {
		Keyboard.enableRepeatEvents(false);
		screen.onClose();
	}

	@Override
	public boolean doesGuiPauseGame() {
		return screen.pausesGame();
	}
}
