package dev.honeycrisp.ui.screens;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.platform.Platform;
import dev.honeycrisp.platform.Renderer;
import dev.honeycrisp.ui.Screen;
import dev.honeycrisp.ui.widgets.Theme;
import dev.honeycrisp.ui.widgets.Ui;

/** Shared frame setup (tokens, input router, overlay) for the core screens. */
abstract class BaseScreen extends Screen {
	protected final Ui ui = new Ui();

	@Override
	public final void render(Renderer r, float mx, float my, float partialTick) {
		Theme.frame();
		ui.begin();
		draw(r, mx, my, partialTick);
		ui.renderOverlay(r, mx, my);
	}

	protected abstract void draw(Renderer r, float mx, float my, float partialTick);

	@Override
	public boolean mouseClicked(float x, float y, int button) {
		return ui.mouseClicked(x, y, button);
	}

	@Override
	public boolean mouseReleased(float x, float y, int button) {
		return ui.mouseReleased(x, y, button);
	}

	@Override
	public boolean mouseDragged(float x, float y, int button, float dx, float dy) {
		return ui.mouseDragged(x, y, button);
	}

	@Override
	public boolean mouseScrolled(float x, float y, float amount) {
		return ui.mouseScrolled(x, y, amount);
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		return ui.keyPressed(key, modifiers);
	}

	@Override
	public boolean charTyped(char c) {
		return ui.charTyped(c);
	}

	static Platform platform() {
		return Honeycrisp.platform();
	}

	static void open(Honeycrisp.ScreenFactory f) {
		Platform p = platform();
		if (f != null && p != null) p.openScreen(f.create());
	}

	static String clientVersion() {
		String v = Honeycrisp.class.getPackage() != null ? Honeycrisp.class.getPackage().getImplementationVersion() : null;
		return v != null ? v : "0.1";
	}
}
