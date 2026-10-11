package dev.honeycrisp.v1_21;

import dev.honeycrisp.ui.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Hosts a core Screen: forwards init/render/input in GUI-scaled coordinates with GLFW codes (1.21.5–1.21.8 raw input args). */
public class McScreen extends net.minecraft.client.gui.screens.Screen {
	private final Screen screen;

	public McScreen(Screen screen) {
		super(Component.empty());
		this.screen = screen;
	}

	public static long window() {
		return Minecraft.getInstance().getWindow().getWindow();
	}

	@Override
	protected void init() {
		screen.init(width, height);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float a) {
		if (!screen.transparentBackground()) super.renderBackground(graphics, mouseX, mouseY, a);
		else if (minecraft.level == null) renderPanorama(graphics, a);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
		if (McRenderer.RENDER_DRAWS_BACKGROUND) renderBackground(graphics, mouseX, mouseY, a);
		float mx = (float) minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
		float my = (float) minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
		McRenderer.draw(graphics, () -> screen.render(McRenderer.INSTANCE, mx, my, a));
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		return screen.mouseClicked((float) x, (float) y, button);
	}

	@Override
	public boolean mouseReleased(double x, double y, int button) {
		return screen.mouseReleased((float) x, (float) y, button);
	}

	@Override
	public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
		return screen.mouseDragged((float) x, (float) y, button, (float) dx, (float) dy);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		return screen.mouseScrolled((float) x, (float) y, (float) scrollY);
	}

	@Override
	public boolean keyPressed(int key, int scancode, int mods) {
		return screen.keyPressed(key, mods) || super.keyPressed(key, scancode, mods);
	}

	@Override
	public boolean charTyped(char c, int mods) {
		return screen.charTyped(c);
	}

	@Override
	public boolean isPauseScreen() {
		return screen.pausesGame();
	}

	@Override
	public void removed() {
		screen.onClose();
	}
}
