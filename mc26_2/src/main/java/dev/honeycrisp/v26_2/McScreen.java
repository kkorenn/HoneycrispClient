package dev.honeycrisp.v26_2;

import dev.honeycrisp.ui.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Hosts a core Screen: forwards init/render/input in GUI-scaled coordinates with GLFW codes. */
public class McScreen extends net.minecraft.client.gui.screens.Screen {
	private final Screen screen;

	public McScreen(Screen screen) {
		super(Component.empty());
		this.screen = screen;
	}

	@Override
	protected void init() {
		screen.init(width, height);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (!screen.transparentBackground()) super.extractBackground(graphics, mouseX, mouseY, a);
		else if (minecraft.level == null) extractPanorama(graphics, a);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		float mx = (float) minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
		float my = (float) minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
		McRenderer.draw(graphics, () -> screen.render(McRenderer.INSTANCE, mx, my, a));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return screen.mouseClicked((float) event.x(), (float) event.y(), event.button());
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return screen.mouseReleased((float) event.x(), (float) event.y(), event.button());
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return screen.mouseDragged((float) event.x(), (float) event.y(), event.button(), (float) dx, (float) dy);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		return screen.mouseScrolled((float) x, (float) y, (float) scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return screen.keyPressed(event.key(), event.modifiers()) || super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		int cp = event.codepoint();
		return Character.isBmpCodePoint(cp) && screen.charTyped((char) cp);
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
