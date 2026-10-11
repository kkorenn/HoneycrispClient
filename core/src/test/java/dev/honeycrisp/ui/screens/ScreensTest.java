package dev.honeycrisp.ui.screens;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.module.Module;
import dev.honeycrisp.module.ModuleRegistry;
import dev.honeycrisp.modules.Features;
import dev.honeycrisp.modules.FpsModule;
import dev.honeycrisp.modules.KeystrokesModule;
import dev.honeycrisp.modules.ZoomModule;
import dev.honeycrisp.platform.GameInfo;
import dev.honeycrisp.platform.Keys;
import dev.honeycrisp.ui.Screen;
import dev.honeycrisp.ui.draw.FakePlatform;
import dev.honeycrisp.ui.draw.Java2DRenderer;
import dev.honeycrisp.ui.widgets.Theme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Renders every screen headlessly to build/test-output/screens/*.png and drives them with simulated input. */
class ScreensTest {
	static final int W = 960, H = 540;
	static final float SCALE = 2;
	static BufferedImage world;
	Java2DRenderer r;
	FakePlatform p;
	ModuleRegistry modules;

	@BeforeEach
	void setUp() throws Exception {
		r = new Java2DRenderer((int) (W * SCALE), (int) (H * SCALE), SCALE);
		r.blurSupported = true;
		p = new FakePlatform(r, W, H, SCALE).install();
		p.time = 100_000;
		p.game = new FakeGame();
		modules = Features.createRegistry();
		Field f = Honeycrisp.class.getDeclaredField("modules");
		f.setAccessible(true);
		f.set(null, modules);
		UiPrefs.get().theme.set(UiPrefs.Appearance.DARK);
	}

	// ---- rendering

	/** Renders frames 33 ms apart (springs settle), then saves the last one. */
	void shoot(Screen s, String name, float mx, float my) throws Exception {
		for (int i = 0; i < 20; i++) frame(s, mx, my);
		File out = new File("build/test-output/screens/" + name + ".png");
		out.getParentFile().mkdirs();
		ImageIO.write(r.canvas, "png", out);
	}

	void frame(Screen s, float mx, float my) {
		p.time += 33;
		paintWorld();
		s.render(r, mx, my, 0);
	}

	void paintWorld() {
		if (world == null) world = makeWorld(r.canvas.getWidth(), r.canvas.getHeight());
		r.g.setComposite(java.awt.AlphaComposite.Src);
		r.g.drawImage(world, 0, 0, null);
		r.g.setComposite(java.awt.AlphaComposite.SrcOver);
	}

	/** A Minecraft-ish landscape: sky, hills of grass/dirt blocks, a few trees, a hotbar. */
	static BufferedImage makeWorld(int w, int h) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
		Graphics2D g = img.createGraphics();
		g.setPaint(new GradientPaint(0, 0, new Color(0x5F8FE0), 0, h * 0.6f, new Color(0xB9D4F5)));
		g.fillRect(0, 0, w, h);
		Random rnd = new Random(7);
		g.setColor(new Color(255, 255, 255, 200));
		for (int i = 0; i < 9; i++) {
			int cx = rnd.nextInt(w), cy = 60 + rnd.nextInt(h / 4), cw = 120 + rnd.nextInt(220);
			for (int j = 0; j < 4; j++) g.fillRect(cx + j * 24 - cw / 2, cy + (j % 2) * 16, cw, 32);
		}
		int b = 32;
		for (int x = 0; x < w; x += b) {
			int ground = (int) (h * 0.58 + Math.sin(x / 260.0) * 70 + Math.sin(x / 90.0) * 22);
			ground -= ground % b;
			for (int y = ground; y < h; y += b) {
				int depth = (y - ground) / b;
				Color c = depth == 0 ? new Color(0x5DA03A) : depth < 3 ? new Color(0x8A5A3A) : new Color(0x7D7D7D);
				int v = rnd.nextInt(18) - 9;
				g.setColor(new Color(clamp(c.getRed() + v), clamp(c.getGreen() + v), clamp(c.getBlue() + v)));
				g.fillRect(x, y, b, b);
				if (depth == 0) {
					g.setColor(new Color(0x4C8A2E));
					g.fillRect(x, y + b - 8, b, 8);
				}
			}
			if (rnd.nextInt(9) == 0 && x > 64 && x < w - 64) {
				g.setColor(new Color(0x6B4A2B));
				g.fillRect(x, ground - 4 * b, b, 4 * b);
				g.setColor(new Color(0x3F7F2A));
				g.fillRect(x - 2 * b, ground - 7 * b, 5 * b, 3 * b);
				g.fillRect(x - b, ground - 8 * b, 3 * b, b);
			}
		}
		// vanilla hotbar + hearts (the HUD editor shows it)
		int hw = 364, hx = (w - hw) / 2, hy = h - 44 - 4;
		g.setColor(new Color(0, 0, 0, 150));
		g.fillRect(hx, hy, hw, 44);
		g.setColor(new Color(255, 255, 255, 70));
		for (int i = 0; i < 9; i++) g.drawRect(hx + 2 + i * 40, hy + 2, 39, 39);
		g.setColor(new Color(255, 255, 255, 220));
		g.drawRect(hx, hy, 43, 43);
		g.setColor(new Color(0xE0352B));
		for (int i = 0; i < 10; i++) g.fillRect(hx + i * 16, hy - 22, 14, 13);
		g.dispose();
		return img;
	}

	static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	ModMenu modMenu() {
		ModMenu m = new ModMenu();
		m.init(W, H);
		return m;
	}

	@Test
	void mainMenu() throws Exception {
		MainMenu m = new MainMenu();
		m.init(W, H);
		MenuActions.set(new MenuActions() {
			public void singleplayer() {
			}

			public void multiplayer() {
			}

			public void options() {
			}

			public void language() {
			}

			public void quit() {
			}
		});
		try {
			shoot(m, "main-menu", 480, 330);
		} finally {
			MenuActions.set(null);
		}
	}

	@Test
	void modMenuAll() throws Exception {
		shoot(modMenu(), "mod-menu-all", 600, 200);
	}

	@Test
	void modMenuLight() throws Exception {
		UiPrefs.get().theme.set(UiPrefs.Appearance.LIGHT);
		shoot(modMenu(), "mod-menu-light", 600, 200);
	}

	@Test
	void modMenuSheet() throws Exception {
		ModMenu m = modMenu();
		frame(m, 0, 0);
		m.openSettings(modules.get(KeystrokesModule.class));
		shoot(m, "mod-menu-sheet", 0, 0);
	}

	@Test
	void colorPicker() throws Exception {
		ModMenu m = modMenu();
		frame(m, 0, 0);
		m.openSettings(modules.get(KeystrokesModule.class));
		for (int i = 0; i < 20; i++) frame(m, 0, 0);
		dev.honeycrisp.ui.widgets.ColorWell well = find(m, dev.honeycrisp.ui.widgets.ColorWell.class);
		assertNotNull(well);
		click(m, well.x + well.w / 2, well.y + well.h / 2);
		assertNotNull(m.ui.overlay());
		shoot(m, "mod-menu-color-picker", 0, 0);
		m.keyPressed(Keys.ESCAPE, 0);
		assertNull(m.ui.overlay(), "Esc closes the picker first");
		assertTrue(m.sheet().isOpen());
	}

	@Test
	void modMenuSheetNonHud() throws Exception {
		ModMenu m = modMenu();
		frame(m, 0, 0);
		m.openSettings(modules.get(ZoomModule.class));
		shoot(m, "mod-menu-sheet-zoom", 0, 0);
	}

	@Test
	void settings() throws Exception {
		shoot(modMenu().show(ModMenu.Section.SETTINGS), "settings", 0, 0);
	}

	@Test
	void cosmetics() throws Exception {
		shoot(modMenu().show(ModMenu.Section.COSMETICS), "cosmetics", 0, 0);
	}

	@Test
	void hudEditor() throws Exception {
		HudEditor e = new HudEditor();
		e.init(W, H);
		assertTrue(modules.suppressHud);
		modules.get(KeystrokesModule.class).setEnabled(true);
		frame(e, 0, 0);
		e.select(modules.get(KeystrokesModule.class));
		shoot(e, "hud-editor", 0, 0);
		e.onClose();
		assertFalse(modules.suppressHud);
	}

	// ---- input

	ModCard cardFor(ModMenu m, Module mod) {
		return m.card(mod);
	}

	void click(Screen s, float x, float y) {
		s.mouseClicked(x, y, 0);
		s.mouseReleased(x, y, 0);
	}

	@Test
	void clickingCardToggleDisablesModule() {
		ModMenu m = modMenu();
		FpsModule fps = modules.get(FpsModule.class);
		assertTrue(fps.enabled());
		frame(m, 0, 0);
		ModCard c = cardFor(m, fps);
		click(m, c.toggle.x + c.toggle.w / 2, c.toggle.y + c.toggle.h / 2);
		assertFalse(fps.enabled());
		frame(m, 0, 0);
		click(m, c.x + 30, c.y + 70);
		assertTrue(fps.enabled(), "clicking the card body toggles too");
	}

	@Test
	void searchFiltersCards() {
		ModMenu m = modMenu();
		frame(m, 0, 0);
		int all = m.visibleMods().size();
		assertEquals(ModMenu.mods().size(), all);
		for (char ch : "zoo".toCharArray()) m.charTyped(ch);
		frame(m, 0, 0);
		List<Module> found = m.visibleMods();
		assertEquals(1, found.size());
		assertSame(modules.get(ZoomModule.class), found.get(0));
		m.keyPressed(Keys.ESCAPE, 0);
		assertEquals(all, m.visibleMods().size(), "Esc clears the search");
		for (char ch : "qqqq".toCharArray()) m.charTyped(ch);
		assertTrue(m.visibleMods().isEmpty());
	}

	@Test
	void emptySearchScreenshot() throws Exception {
		ModMenu m = modMenu();
		for (char ch : "elytra".toCharArray()) m.charTyped(ch);
		shoot(m, "mod-menu-empty-search", 0, 0);
	}

	@Test
	void keyCaptureSetsKeybind() {
		ModMenu m = modMenu();
		ZoomModule zoom = modules.get(ZoomModule.class);
		frame(m, 0, 0);
		m.openSettings(zoom);
		for (int i = 0; i < 30; i++) frame(m, 0, 0);
		dev.honeycrisp.ui.widgets.KeybindButton kb = find(m, dev.honeycrisp.ui.widgets.KeybindButton.class);
		assertNotNull(kb);
		click(m, kb.x + kb.w / 2, kb.y + kb.h / 2);
		assertTrue(kb.isFocused(), "listening");
		assertTrue(m.keyPressed(Keys.V, 0));
		assertEquals(Keys.V, zoom.key.key());
		assertFalse(kb.isFocused());
		m.charTyped('v');
		assertEquals("", m.searchField().text(), "the captured key's char doesn't start a search");
		click(m, kb.x + kb.w / 2, kb.y + kb.h / 2);
		m.keyPressed(Keys.ESCAPE, 0);
		assertEquals(Keys.V, zoom.key.key(), "Esc cancels capture");
		assertTrue(m.sheet().isOpen(), "Esc while listening doesn't close the sheet");
		click(m, kb.x + kb.w / 2, kb.y + kb.h / 2);
		m.keyPressed(Keys.BACKSPACE, 0);
		assertEquals(Keys.UNKNOWN, zoom.key.key(), "⌫ unbinds");
		m.keyPressed(Keys.ESCAPE, 0);
		assertFalse(m.sheet().isOpen(), "then Esc closes the sheet");
	}

	@Test
	void sheetCoversEverySettingType() {
		ModMenu m = modMenu();
		frame(m, 0, 0);
		for (Module mod : ModMenu.mods()) {
			m.openSettings(mod);
			for (int i = 0; i < 3; i++) frame(m, 0, 0);
		}
		// TextSetting has no module yet: exercise its control directly.
		dev.honeycrisp.module.TextSetting ts = new dev.honeycrisp.module.TextSetting("t", "Label", "", 8);
		dev.honeycrisp.ui.widgets.Widget[] c = dev.honeycrisp.ui.widgets.Form.controls(null, ts);
		assertTrue(c[0] instanceof dev.honeycrisp.ui.widgets.TextField);
		dev.honeycrisp.ui.widgets.TextField tf = (dev.honeycrisp.ui.widgets.TextField) c[0];
		for (char ch : "abcdefghij".toCharArray()) tf.charTyped(ch);
		assertEquals("abcdefgh", ts.get());
	}

	@Test
	void hudEditorDragMovesAndSnaps() {
		HudEditor e = new HudEditor();
		e.init(W, H);
		FpsModule fps = modules.get(FpsModule.class);
		frame(e, 0, 0);
		float x0 = fps.x(W), y0 = fps.y(H);
		float gx = x0 + 3, gy = y0 + 3;
		e.mouseClicked(gx, gy, 0);
		assertSame(fps, e.selected());
		e.mouseDragged(gx + 200, gy + 150, 0, 200, 150);
		e.mouseReleased(gx + 200, gy + 150, 0);
		frame(e, 0, 0);
		assertEquals(x0 + 200, fps.x(W), 0.6);
		assertEquals(y0 + 150, fps.y(H), 0.6);
		// near the horizontal center: snaps the module's center onto it
		float mx = fps.x(W) + fps.width() / 2, my = fps.y(H) + fps.height() / 2, tx = W / 2f + 3;
		e.mouseClicked(mx, my, 0);
		e.mouseDragged(tx, my, 0, 0, 0);
		e.mouseReleased(tx, my, 0);
		frame(e, 0, 0);
		assertEquals(W / 2f, fps.x(W) + fps.width() / 2, 0.6);
		// wheel scales
		double s = fps.scale.value();
		e.mouseScrolled(fps.x(W) + fps.width() / 2, fps.y(H) + fps.height() / 2, 1);
		assertEquals(s + 0.05, fps.scale.value(), 1e-6);
		e.onClose();
	}

	@Test
	void settingsThemeSwitchAndAccent() {
		ModMenu m = modMenu().show(ModMenu.Section.SETTINGS);
		frame(m, 0, 0);
		dev.honeycrisp.ui.widgets.Segmented seg = find(m, dev.honeycrisp.ui.widgets.Segmented.class);
		click(m, seg.x + seg.w * 5 / 6, seg.y + seg.h / 2);
		assertEquals(UiPrefs.Appearance.LIGHT, UiPrefs.get().theme.get());
		frame(m, 0, 0);
		assertFalse(Theme.dark);
	}

	@Test
	void editHudOpensEditor() {
		ModMenu m = modMenu();
		for (int i = 0; i < 20; i++) frame(m, 0, 0);
		Screen before = p.opened;
		dev.honeycrisp.ui.widgets.Button b = null;
		for (Object w : widgets(m)) if (w instanceof dev.honeycrisp.ui.widgets.Button && "Edit HUD".equals(((dev.honeycrisp.ui.widgets.Button) w).label)) b = (dev.honeycrisp.ui.widgets.Button) w;
		assertNotNull(b);
		click(m, b.x + b.w / 2, b.y + b.h / 2);
		assertNotSame(before, p.opened);
		assertTrue(p.opened instanceof HudEditor);
	}

	@SuppressWarnings("unchecked")
	static List<Object> widgets(BaseScreen s) {
		try {
			Field f = dev.honeycrisp.ui.widgets.Ui.class.getDeclaredField("widgets");
			f.setAccessible(true);
			return (List<Object>) f.get(s.ui);
		} catch (ReflectiveOperationException e) {
			return Collections.emptyList();
		}
	}

	static <T> T find(BaseScreen s, Class<T> type) {
		for (Object w : widgets(s)) if (type.isInstance(w)) return type.cast(w);
		return null;
	}

	static final class FakeGame implements GameInfo {
		public boolean inWorld() { return true; }
		public int fps() { return 240; }
		public int ping() { return 42; }
		public double x() { return 128.5; }
		public double y() { return 64; }
		public double z() { return -2048.25; }
		public float yaw() { return 30; }
		public float pitch() { return 0; }
		public String biome() { return "Plains"; }
		public String server() { return "play.example.net"; }
		public long dayTime() { return 6000; }
		public String playerName() { return "Steve"; }
		public boolean sprinting() { return false; }
		public boolean sneaking() { return false; }
		public ItemInfo heldItem() { return null; }
		public ItemInfo[] armor() { return new ItemInfo[4]; }
		public List<EffectInfo> effects() { return Collections.emptyList(); }
	}
}
