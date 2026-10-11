package dev.honeycrisp.ui.screens;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.Module;
import dev.honeycrisp.platform.GameInfo;
import dev.honeycrisp.platform.Keys;
import dev.honeycrisp.platform.Platform;
import dev.honeycrisp.platform.Renderer;
import dev.honeycrisp.ui.widgets.Button;
import dev.honeycrisp.ui.widgets.Gfx;
import dev.honeycrisp.ui.widgets.Motion;
import dev.honeycrisp.ui.widgets.ScrollView;
import dev.honeycrisp.ui.widgets.TextField;
import dev.honeycrisp.ui.widgets.Theme;
import dev.honeycrisp.ui.widgets.Value;
import dev.honeycrisp.ui.widgets.Widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static dev.honeycrisp.ui.widgets.Theme.*;

/** The Right Shift mod menu (DESIGN 4.2): floating sidebar, toolbar with search, card grid, per-mod sheet. */
public class ModMenu extends BaseScreen {
	public enum Section {
		ALL("All", "square.grid.2x2", null), HUD(Category.HUD), PVP(Category.PVP), CAMERA(Category.CAMERA),
		VISUAL(Category.VISUAL), COSMETICS(Category.COSMETICS), SETTINGS("Settings", "gearshape", null);

		public final String label, glyph;
		public final Category category;

		Section(Category c) {
			this(c.displayName, c.sfSymbol, c);
		}

		Section(String label, String glyph, Category category) {
			this.label = label;
			this.glyph = glyph;
			this.category = category;
		}
	}

	private static final int MOD_SUPER = 8;
	private Section section = Section.ALL;
	private final Value<String> query = Value.local("");
	private final TextField search = new TextField(query, "Search", true);
	private final Button editHud = new Button("Edit HUD", "rectangle.3.group", Button.Size.REGULAR, Button.Style.SECONDARY, new Runnable() {
		public void run() {
			openHudEditor();
		}
	});
	private final ScrollView scroll = new ScrollView();
	private final ModuleSheet sheet = new ModuleSheet(ui);
	private final Map<Module, ModCard> cards = new IdentityHashMap<Module, ModCard>();
	private final List<SidebarRow> rows = new ArrayList<SidebarRow>();
	private final Motion openAnim = new Motion(Motion.SMOOTH, 0), highlight = new Motion(Motion.SNAPPY, -1);
	private final SettingsPane settingsPane = new SettingsPane(ui);
	private final CosmeticsPane cosmeticsPane = new CosmeticsPane(ui);
	private List<Module> order = new ArrayList<Module>();
	private Section orderedFor;
	private String orderedQuery;
	private long sectionAt = Long.MIN_VALUE;
	/** Window rect, for tests and the sheet. */
	float wx, wy, ww, wh;

	public ModMenu() {
		for (Section s : Section.values()) rows.add(new SidebarRow(s));
	}

	/** Selects a sidebar section (e.g. Cosmetics from the main menu). */
	public ModMenu show(Section s) {
		if (s != section) {
			section = s;
			sectionAt = Theme.now;
			scroll.reset();
		}
		return this;
	}

	public Section section() {
		return section;
	}

	static void openHudEditor() {
		open(Honeycrisp.screens().hudEditor);
	}

	static List<Module> mods() {
		List<Module> out = new ArrayList<Module>();
		if (Honeycrisp.modules() == null) return out;
		for (Module m : Honeycrisp.modules().all()) if (m.category != Category.GENERAL) out.add(m);
		return out;
	}

	private int count(Section s) {
		int n = 0;
		for (Module m : mods()) if (s == Section.ALL || m.category == s.category) n++;
		return n;
	}

	/** Visible modules: the section's, or search matches across all sections by score. */
	List<Module> visibleMods() {
		final String q = query.get().trim().toLowerCase(Locale.ROOT);
		if (section == orderedFor && q.equals(orderedQuery)) return order;
		List<Module> out = new ArrayList<Module>();
		for (Module m : mods()) {
			if (!q.isEmpty() ? score(m, q) > 0 : section == Section.ALL || m.category == section.category) out.add(m);
		}
		Collections.sort(out, new Comparator<Module>() {
			public int compare(Module a, Module b) {
				if (!q.isEmpty() && score(a, q) != score(b, q)) return score(b, q) - score(a, q);
				if (q.isEmpty() && a.enabled() != b.enabled()) return a.enabled() ? -1 : 1;
				return a.name.compareToIgnoreCase(b.name);
			}
		});
		orderedFor = section;
		orderedQuery = q;
		return order = out;
	}

	static int score(Module m, String q) {
		String n = m.name.toLowerCase(Locale.ROOT);
		if (n.startsWith(q)) return 4;
		if (n.contains(" " + q)) return 3;
		if (n.contains(q)) return 2;
		if (m.description.toLowerCase(Locale.ROOT).contains(q) || m.category.displayName.toLowerCase(Locale.ROOT).contains(q)) return 1;
		return 0;
	}

	ModCard card(final Module m) {
		ModCard c = cards.get(m);
		if (c == null) cards.put(m, c = new ModCard(m, new Runnable() {
			public void run() {
				openSettings(m);
			}
		}));
		return c;
	}

	void openSettings(Module m) {
		ui.focus(null);
		sheet.open(m);
	}

	ModuleSheet sheet() {
		return sheet;
	}

	TextField searchField() {
		return search;
	}

	@Override
	protected void draw(Renderer r, float mx, float my, float partialTick) {
		float W = width, H = height;
		float t = openAnim.to(1);
		float old = Gfx.opacity;
		Gfx.opacity = Math.min(1, t);
		Gfx.fill(r, 0, 0, W, H, scrim);
		// 2.8: window y +12 → 0 with the fade; reduce motion keeps only the fade
		ww = Math.min(W - u(16), clamp(W * 0.78f, u(720), u(1120)));
		wh = Math.min(H - u(16), clamp(H * 0.80f, u(460), u(760)));
		wx = Math.round((W - ww) / 2);
		wy = Math.round((H - wh) / 2 + (reduceMotion ? 0 : (1 - t) * u(12)));
		// While the sheet is up, the window behind gets no hover.
		float hx = sheet.isOpen() ? -1e6f : mx, hy = sheet.isOpen() ? -1e6f : my;
		Gfx.windowGlass(r, wx, wy, ww, wh, u(24));
		sidebar(r, hx, hy);
		content(r, hx, hy);
		if (sheet.visible()) {
			float st = sheet.isOpen() ? 1 : 0;
			Gfx.rect(r, wx, wy, ww, wh, u(24), alpha(0x26000000, st));
			sheet.render(r, W, H, mx, my);
		}
		Gfx.opacity = old;
	}

	private static float clamp(float v, float lo, float hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	// ---- sidebar (3.1)

	private void sidebar(Renderer r, float mx, float my) {
		float sx = wx + u(8), sy = wy + u(8), sw = u(200), sh = wh - u(16);
		Gfx.groupGlass(r, sx, sy, sw, sh, u(16), false);
		float rx = sx + u(8), rw = sw - u(16), rh = u(30), y = sy + u(12);
		float footerTop = sy + sh - u(8 + 36);
		float settingsY = footerTop - u(8) - rh;
		int sel = section.ordinal();
		float[] ys = new float[rows.size()];
		for (int i = 0; i < rows.size(); i++) {
			Section s = rows.get(i).section;
			ys[i] = s == Section.SETTINGS ? settingsY : y;
			if (s != Section.SETTINGS) y += rh + u(2);
		}
		if (highlight.value() < 0) highlight.snap(ys[sel]);
		float hy = reduceMotion ? ys[sel] : highlight.to(ys[sel]);
		Gfx.rect(r, rx, hy, rw, rh, u(8), accentTint);
		for (int i = 0; i < rows.size(); i++) {
			SidebarRow row = rows.get(i);
			row.at(rx, ys[i], rw, rh);
			ui.add(row).render(r, mx, my);
		}
		// footer: account
		Gfx.fill(r, sx + u(12), footerTop, sw - u(24), hairline, separator);
		Platform p = platform();
		GameInfo g = p != null ? p.game() : null;
		String name = g != null && g.playerName() != null && !g.playerName().isEmpty() ? g.playerName() : "Player";
		float fy = footerTop + u(4) + u(18);
		Gfx.iconAt(r, "person.crop.circle.fill", rx + u(10 + 8), fy, u(20), label2);
		Gfx.textMid(r, Gfx.truncate(name, body, rw - u(44)), rx + u(34), fy, body, label);
	}

	private final class SidebarRow extends Widget {
		final Section section;

		SidebarRow(Section section) {
			this.section = section;
		}

		@Override
		public void render(Renderer r, float mx, float my) {
			boolean sel = section == ModMenu.this.section;
			if (!sel && hovered(mx, my)) Gfx.rect(r, x, y, w, h, u(8), fill4);
			float cy = y + h / 2;
			String g = section.glyph;
			Gfx.iconAt(r, sel && !g.endsWith(".fill") && hasFill(g) ? g + ".fill" : g, x + u(10 + 8), cy, u(16), sel ? accent : label2);
			Gfx.textMid(r, section.label, x + u(34), cy, sel ? bodyBold : body, label);
			if (section != Section.COSMETICS && section != Section.SETTINGS) {
				Gfx.textMidRight(r, String.valueOf(count(section)), x + w - u(10), cy, caption, label3);
			}
		}

		@Override
		public boolean mouseClicked(float mx, float my, int button) {
			if (button != 0) return false;
			if (!query.get().isEmpty()) search.setText("");
			show(section);
			return true;
		}
	}

	private static boolean hasFill(String g) {
		return g.equals("gearshape") || g.equals("camera") || g.equals("eye") || g.equals("tshirt") || g.equals("paintpalette");
	}

	// ---- content

	private void content(Renderer r, float mx, float my) {
		float cx = wx + u(8 + 200 + 16), cr = wx + ww - u(16), cw = cr - cx;
		float ty = wy + u(26);
		boolean searching = !query.get().trim().isEmpty();
		String title = searching ? "Search" : section.label;
		// toolbar
		float right = cr;
		boolean modsSection = section != Section.SETTINGS && section != Section.COSMETICS;
		if (modsSection) {
			editHud.style = (section == Section.ALL || section == Section.HUD) && !searching ? Button.Style.PRIMARY : Button.Style.SECONDARY;
			editHud.at(right - editHud.prefW(), ty - u(14), editHud.prefW(), u(28));
			ui.add(editHud).render(r, mx, my);
			right = editHud.x - u(8);
			float sw = Math.min(u(240), right - cx - u(120));
			search.at(right - sw, ty - u(16), sw, u(32));
			ui.add(search).render(r, mx, my);
		}
		Gfx.textMid(r, title, cx, ty, title2, label);
		if (section == Section.COSMETICS) cosmeticsPane.toolbar(r, cx + Gfx.width(title, title2) + u(16), ty, mx, my);
		// pane (cross-fade on section change, 0.15 s, y +6 → 0)
		float top = wy + u(52), viewH = wy + wh - u(8) - top;
		long since = Theme.now - sectionAt;
		float ft = sectionAt == Long.MIN_VALUE || since < 0 ? 1 : Math.min(1, since / 150f);
		ft = 1 - (1 - ft) * (1 - ft) * (1 - ft);
		float old = Gfx.opacity;
		Gfx.opacity *= ft;
		float shift = reduceMotion ? 0 : (1 - ft) * u(6);
		float vx = wx + u(8 + 200), vw = wx + ww - vx;
		if (section == Section.SETTINGS) {
			float fw = Math.min(cw, u(640)), fx = cx + (cw - fw) / 2;
			float y0 = scroll.begin(r, ui, vx, top, vw, viewH, settingsPane.height(fw) + u(8 + 16)) + u(8) + shift;
			settingsPane.render(r, fx, y0, fw, mx, my);
			scroll.end(r, ui, mx, my);
		} else if (section == Section.COSMETICS) {
			float y0 = scroll.begin(r, ui, vx, top, vw, viewH, cosmeticsPane.height(cw) + u(8 + 16)) + u(8) + shift;
			cosmeticsPane.render(r, cx, y0, cw, mx, my);
			scroll.end(r, ui, mx, my);
		} else {
			grid(r, cx, cw, top, viewH, vx, vw, shift, mx, my);
		}
		Gfx.opacity = old;
		if (scroll.offset() > 0.5f) {
			Gfx.fadeDown(r, vx, top, vw - u(1), u(16), dark ? 0x801C1C1E : 0x80F6F6F8);
			Gfx.fill(r, vx, top, vw - u(1), hairline, separator);
		}
	}

	private void grid(Renderer r, float cx, float cw, float top, float viewH, float vx, float vw, float shift, float mx, float my) {
		List<Module> mods = visibleMods();
		String q = query.get().trim();
		if (mods.isEmpty()) {
			float cy = top + viewH * 0.42f;
			Gfx.iconAt(r, "magnifyingglass", cx + cw / 2, cy - u(24), u(32), label3);
			Gfx.textMidCentered(r, "No mods match “" + q + "”", cx + cw / 2, cy + u(14), title3, label);
			Gfx.textMidCentered(r, "Check the spelling or try a different word.", cx + cw / 2, cy + u(36), subheadline, label2);
			return;
		}
		float gut = u(12), cardH = u(120);
		int cols = Math.max(1, (int) Math.floor((cw + gut) / u(228)));
		float cardW = (cw - gut * (cols - 1)) / cols;
		int rowsN = (mods.size() + cols - 1) / cols;
		float contentH = u(8) + rowsN * cardH + (rowsN - 1) * gut + u(16);
		float y0 = scroll.begin(r, ui, vx, top, vw, viewH, contentH) + u(8) + shift;
		for (int i = 0; i < mods.size(); i++) {
			float x = cx + (i % cols) * (cardW + gut), y = y0 + (i / cols) * (cardH + gut);
			if (y > top + viewH || y + cardH < top) continue;
			ModCard c = card(mods.get(i));
			c.at(x, y, cardW, cardH);
			c.render(r, ui, mx, my, q);
		}
		scroll.end(r, ui, mx, my);
	}

	// ---- input

	@Override
	public boolean keyPressed(int key, int mods) {
		if (sheet.isOpen() && ui.focused() == null && ui.overlay() == null && sheet.keyPressed(key, mods)) return true;
		if (super.keyPressed(key, mods)) return true;
		if ((mods & MOD_SUPER) != 0 && key == Keys.F) {
			if (section == Section.SETTINGS || section == Section.COSMETICS) show(Section.ALL);
			ui.focus(search);
			return true;
		}
		if ((mods & MOD_SUPER) != 0 && key >= Keys.KEY_0 + 1 && key <= Keys.KEY_0 + 7) {
			show(Section.values()[key - Keys.KEY_0 - 1]);
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(char c) {
		if (super.charTyped(c)) return true;
		if (sheet.isOpen() || ui.overlay() != null || c <= ' ' || c == 127) return false;
		if (section == Section.SETTINGS || section == Section.COSMETICS) show(Section.ALL);
		ui.focus(search);
		return search.charTyped(c);
	}

	@Override
	public boolean pausesGame() {
		return true;
	}
}
