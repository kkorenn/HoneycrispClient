package dev.honeycrisp.ui.screens;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.platform.GameInfo;
import dev.honeycrisp.platform.Platform;
import dev.honeycrisp.platform.Renderer;
import dev.honeycrisp.ui.widgets.Button;
import dev.honeycrisp.ui.widgets.Gfx;
import dev.honeycrisp.ui.widgets.Theme;

import static dev.honeycrisp.ui.widgets.Theme.*;

/** Title screen replacement (DESIGN 4.1): wordmark, clear-glass button stack, account chip, bottom bar. */
public class MainMenu extends BaseScreen {
	private final Button singleplayer = new Button("Singleplayer", "play.fill", Button.Size.XLARGE, Button.Style.CLEAR, new Runnable() {
		public void run() {
			if (MenuActions.get() != null) MenuActions.get().singleplayer();
		}
	});
	private final Button multiplayer = new Button("Multiplayer", "person.2.fill", Button.Size.XLARGE, Button.Style.CLEAR, new Runnable() {
		public void run() {
			if (MenuActions.get() != null) MenuActions.get().multiplayer();
		}
	});
	private final Button mods = new Button("Mods", "puzzlepiece.extension", Button.Size.LARGE, Button.Style.CLEAR, new Runnable() {
		public void run() {
			open(Honeycrisp.screens().modMenu);
		}
	});
	private final Button settings = new Button("Settings", "gearshape", Button.Size.LARGE, Button.Style.CLEAR, new Runnable() {
		public void run() {
			if (MenuActions.get() != null) MenuActions.get().options();
		}
	});
	private final Button quit = new Button(null, "power", Button.Size.LARGE, Button.Style.CLEAR, new Runnable() {
		public void run() {
			if (MenuActions.get() != null) MenuActions.get().quit();
		}
	});
	private final Button cosmetics = Button.icon("sparkles", Button.Style.CLEAR, new Runnable() {
		public void run() {
			Platform p = platform();
			if (p != null) p.openScreen(new ModMenu().show(ModMenu.Section.COSMETICS));
		}
	});
	private final Button language = Button.icon("globe", Button.Style.CLEAR, new Runnable() {
		public void run() {
			if (MenuActions.get() != null) MenuActions.get().language();
		}
	});

	@Override
	protected void draw(Renderer r, float mx, float my, float partialTick) {
		float W = width, H = height;
		boolean blurred = Theme.blur && !reduceTransparency && r.blur(0, 0, W, H, u(12));
		Gfx.fill(r, 0, 0, W, H, blurred ? 0x40000000 : 0x66000000);
		MenuActions actions = MenuActions.get();
		boolean narrow = W < u(480);
		Platform p = platform();
		// top bar
		String version = "Minecraft " + (p != null ? p.minecraftVersion() : "") + "  ·  Honeycrisp " + clientVersion();
		Gfx.text(r, version, u(12), u(12), footnote, 0x99EBEBF5);
		accountChip(r, W - u(16), u(16));
		// wordmark
		float wmTop = H * 0.26f;
		dev.honeycrisp.ui.draw.Font wm = narrow ? Theme.font(36, 800) : wordmark;
		Gfx.textMidCentered(r, "Honeycrisp", W / 2, wmTop + Gfx.lineHeight(wm) / 2, wm, 0xF2FFFFFF);
		// stack
		float sw = u(narrow ? 260 : 300), sx = (W - sw) / 2, y = wmTop + Gfx.lineHeight(wm) + u(36);
		if (actions != null) {
			ui.add(singleplayer.at(sx, y, sw, u(48))).render(r, mx, my);
			y += u(48 + 10);
			ui.add(multiplayer.at(sx, y, sw, u(48))).render(r, mx, my);
			y += u(48 + 16);
			float qw = u(36), rest = sw - qw - u(16);
			float mw = Math.round(rest * 0.53f);
			ui.add(mods.at(sx, y, mw, u(36))).render(r, mx, my);
			ui.add(settings.at(sx + mw + u(8), y, rest - mw, u(36))).render(r, mx, my);
			ui.add(quit.at(sx + sw - qw, y, qw, qw)).render(r, mx, my);
		} else {
			ui.add(mods.at(sx, y, sw, u(48))).render(r, mx, my);
		}
		// bottom bar
		float by = H - u(16 + 28);
		ui.add(cosmetics.at(u(16), by, u(28), u(28))).render(r, mx, my);
		if (actions != null) ui.add(language.at(u(16 + 28 + 8), by, u(28), u(28))).render(r, mx, my);
		String copy = actions != null ? actions.copyright() : "Copyright Mojang AB. Do not distribute!";
		Gfx.textMidRight(r, copy, W - u(16), by + u(14), footnote, 0x80EBEBF5);
		if (cosmetics.hovered(mx, my)) ui.tooltip("Cosmetics", mx, my);
		if (language.hovered(mx, my)) ui.tooltip("Language", mx, my);
		if (quit.hovered(mx, my)) ui.tooltip("Quit Game", mx, my);
	}

	private void accountChip(Renderer r, float right, float top) {
		Platform p = platform();
		GameInfo g = p != null ? p.game() : null;
		String name = g != null && g.playerName() != null && !g.playerName().isEmpty() ? g.playerName() : "Player";
		dev.honeycrisp.ui.draw.Font f = Theme.callout.withWeight(600);
		float h = u(32), w = u(4 + 24 + 8) + Gfx.width(name, f) + u(14);
		float x = right - w;
		Gfx.clearGlass(r, x, top, w, h, h / 2);
		Gfx.iconAt(r, "person.crop.circle.fill", x + u(4 + 12), top + h / 2, u(24), 0xD9FFFFFF);
		Gfx.textMid(r, name, x + u(4 + 24 + 8), top + h / 2, f, 0xF2FFFFFF);
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		// The title screen has nowhere to go back to.
		return super.keyPressed(key, modifiers) || key == dev.honeycrisp.platform.Keys.ESCAPE;
	}

	@Override
	public boolean transparentBackground() {
		return true;
	}
}
