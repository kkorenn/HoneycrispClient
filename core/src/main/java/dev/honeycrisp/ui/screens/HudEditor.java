package dev.honeycrisp.ui.screens;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.module.HudModule;
import dev.honeycrisp.module.ModuleRegistry;
import dev.honeycrisp.platform.GameInfo;
import dev.honeycrisp.platform.Keys;
import dev.honeycrisp.platform.Platform;
import dev.honeycrisp.platform.Renderer;
import dev.honeycrisp.ui.widgets.Button;
import dev.honeycrisp.ui.widgets.Gfx;
import dev.honeycrisp.ui.widgets.Sheet;
import dev.honeycrisp.ui.widgets.Ui;

import java.util.ArrayList;
import java.util.List;

import static dev.honeycrisp.ui.widgets.Theme.*;

/**
 * HUD editor (DESIGN 4.3): every enabled HUD module drawn live; drag to move with snapping to screen edges/center
 * and other modules, corner handles or the wheel to scale, arrow keys to nudge, Reset All, grid. Owns HUD drawing
 * while open (ModuleRegistry.suppressHud).
 */
public class HudEditor extends BaseScreen {
	static final float SNAP = 4, EDGE = 4, VANILLA_HUD_H = 50;
	private HudModule selected, dragging;
	private int handle = -1;
	private float grabX, grabY, fixedX, fixedY;
	private Float guideX, guideY;
	private boolean grid, snap = true;
	private final ResetSheet resetSheet = new ResetSheet(ui);
	private final Button gridButton = Button.icon("square.grid.3x3", Button.Style.PLAIN, new Runnable() {
		public void run() {
			grid = !grid;
		}
	});
	private final Button snapButton = Button.icon("circle.dashed", Button.Style.PLAIN, new Runnable() {
		public void run() {
			snap = !snap;
		}
	});
	private final Button resetButton = Button.icon("arrow.counterclockwise", Button.Style.PLAIN, new Runnable() {
		public void run() {
			resetSheet.open();
		}
	});
	private final Button modsButton = Button.icon("puzzlepiece.extension", Button.Style.PLAIN, new Runnable() {
		public void run() {
			setSuppress(false);
			open(Honeycrisp.screens().modMenu);
		}
	});
	private final Button done = new Button("Done", null, Button.Size.LARGE, Button.Style.PRIMARY, new Runnable() {
		public void run() {
			Platform p = platform();
			setSuppress(false);
			if (p != null) p.closeScreen();
		}
	});

	@Override
	public void init(int width, int height) {
		super.init(width, height);
		setSuppress(true);
	}

	@Override
	public void onClose() {
		setSuppress(false);
	}

	private static void setSuppress(boolean on) {
		if (Honeycrisp.modules() != null) Honeycrisp.modules().suppressHud = on;
	}

	public HudModule selected() {
		return selected;
	}

	public void select(HudModule m) {
		selected = m;
	}

	static List<HudModule> modules() {
		List<HudModule> out = new ArrayList<HudModule>();
		ModuleRegistry reg = Honeycrisp.modules();
		if (reg != null) for (HudModule m : reg.hud()) if (m.enabled()) out.add(m);
		return out;
	}

	private static GameInfo game() {
		Platform p = platform();
		return p != null ? p.game() : null;
	}

	@Override
	protected void draw(Renderer r, float mx, float my, float partialTick) {
		float W = width, H = height;
		Gfx.fill(r, 0, 0, W, H, 0x33000000);
		if (grid) {
			for (int i = 1; i * 8 < W; i++) Gfx.fill(r, i * 8, 0, hairline, H, i % 4 == 0 ? 0x26FFFFFF : 0x14FFFFFF);
			for (int j = 1; j * 8 < H; j++) Gfx.fill(r, 0, j * 8, W, hairline, j % 4 == 0 ? 0x26FFFFFF : 0x14FFFFFF);
		}
		if (dragging != null) {
			Gfx.fill(r, W / 2 - hairline / 2, 0, hairline, H, 0x33FFFFFF);
			Gfx.fill(r, 0, H / 2 - hairline / 2, W, hairline, 0x33FFFFFF);
		}
		GameInfo g = game();
		List<HudModule> mods = modules();
		boolean sheet = resetSheet.isOpen();
		HudModule hover = sheet || dragging != null ? null : moduleAt(mx, my);
		for (HudModule m : mods) {
			try {
				m.draw(r, g, partialTick, true, W, H);
			} catch (RuntimeException e) {
				continue;
			}
			float x = m.x(W), y = m.y(H), w = m.width(), h = m.height(), rad = 4 * m.scale.floatValue() + 2;
			if (w <= 0) continue;
			int c = m == selected ? 0 : m == hover ? accent : 0x59FFFFFF;
			if (c != 0) Gfx.stroke(r, x - 2, y - 2, w + 4, h + 4, rad, u(1), c);
		}
		if (selected != null && mods.contains(selected) && selected.width() > 0) selection(r, selected, W, H);
		if (guideX != null) Gfx.fill(r, guideX - u(0.5f), 0, u(1), H, accent);
		if (guideY != null) Gfx.fill(r, 0, guideY - u(0.5f), W, u(1), accent);
		if (mods.isEmpty()) {
			Gfx.textMidCentered(r, "No HUD mods are enabled", W / 2, H / 2 - u(10), title3, 0xF2FFFFFF);
			Gfx.textMidCentered(r, "Turn some on in the mod menu.", W / 2, H / 2 + u(12), subheadline, 0x99EBEBF5);
		}
		toolbar(r, W, H, sheet ? -1e6f : mx, sheet ? -1e6f : my);
		resetSheet.render(r, W, H, mx, my);
	}

	private void selection(Renderer r, HudModule m, float W, float H) {
		float x = m.x(W) - 2, y = m.y(H) - 2, w = m.width() + 4, h = m.height() + 4;
		Gfx.stroke(r, x, y, w, h, 4 * m.scale.floatValue() + 2, u(1.5f), accent);
		float d = u(8);
		for (int i = 0; i < 4; i++) {
			float hx = (i % 2 == 0 ? x : x + w) - d / 2, hy = (i < 2 ? y : y + h) - d / 2;
			Gfx.shadow(r, hx, hy + u(1), d, d, d / 2, u(3), 0x40000000);
			Gfx.capsule(r, hx, hy, d, d, 0xFFFFFFFF);
			Gfx.stroke(r, hx, hy, d, d, d / 2, u(1.5f), accent);
		}
		String chip = m.name + "  ·  " + String.format(java.util.Locale.ROOT, "%.2f×", m.scale.value());
		float cw = Gfx.width(chip, caption2) + u(16), ch = u(20);
		float cx = Math.max(u(4), Math.min(x + w / 2 - cw / 2, W - cw - u(4)));
		float cy = y - ch - u(8);
		if (cy < u(4)) cy = y + h + u(8);
		Gfx.shadow(r, cx, cy + u(1), cw, ch, ch / 2, u(4), 0x40000000);
		Gfx.capsule(r, cx, cy, cw, ch, 0xE61C1C1E);
		Gfx.textMidCentered(r, chip, cx + cw / 2, cy + ch / 2, caption2, 0xF2FFFFFF);
	}

	private void toolbar(Renderer r, float W, float H, float mx, float my) {
		Button[] icons = {gridButton, snapButton, resetButton, modsButton};
		gridButton.selected = grid;
		snapButton.selected = snap;
		float in = u(4), b = u(32), dw = Math.max(u(72), done.prefW());
		float tw = in + icons.length * (b + u(2)) + u(8) + dw + in, th = u(40);
		// Above the vanilla hotbar, hearts and armor (~50 GUI px), so the toolbar never covers them.
		float tx = (W - tw) / 2, ty = H - VANILLA_HUD_H - u(16) - th;
		Gfx.windowGlass(r, tx, ty, tw, th, th / 2);
		float x = tx + in;
		String[] tips = {grid ? "Hide Grid" : "Show Grid", snap ? "Turn Snapping Off" : "Turn Snapping On", "Reset All Positions", "Back to Mods"};
		for (int i = 0; i < icons.length; i++) {
			icons[i].at(x, ty + in, b, b);
			ui.add(icons[i]).render(r, mx, my);
			if (icons[i].hovered(mx, my)) ui.tooltip(tips[i], mx, my);
			x += b + u(2);
		}
		Gfx.fill(r, x + u(3), ty + u(12), hairline, th - u(24), separator);
		done.at(tx + tw - in - dw, ty + in, dw, b);
		ui.add(done).render(r, mx, my);
	}

	HudModule moduleAt(float mx, float my) {
		List<HudModule> mods = modules();
		for (int i = mods.size() - 1; i >= 0; i--) {
			HudModule m = mods.get(i);
			float x = m.x(width), y = m.y(height);
			if (m.width() > 0 && mx >= x - 2 && mx < x + m.width() + 2 && my >= y - 2 && my < y + m.height() + 2) return m;
		}
		return null;
	}

	private int handleAt(float mx, float my) {
		if (selected == null || !modules().contains(selected)) return -1;
		float x = selected.x(width) - 2, y = selected.y(height) - 2, w = selected.width() + 4, h = selected.height() + 4, hit = u(8);
		for (int i = 0; i < 4; i++) {
			float hx = i % 2 == 0 ? x : x + w, hy = i < 2 ? y : y + h;
			if (Math.abs(mx - hx) <= hit && Math.abs(my - hy) <= hit) return i;
		}
		return -1;
	}

	// ---- input

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (ui.mouseClicked(mx, my, button)) return true;
		if (button != 0) return false;
		handle = handleAt(mx, my);
		if (handle >= 0) {
			float x = selected.x(width), y = selected.y(height);
			fixedX = handle % 2 == 0 ? x + selected.width() : x;
			fixedY = handle < 2 ? y + selected.height() : y;
			return true;
		}
		HudModule m = moduleAt(mx, my);
		selected = m;
		if (m != null) {
			dragging = m;
			grabX = mx - m.x(width);
			grabY = my - m.y(height);
		}
		return true;
	}

	@Override
	public boolean mouseDragged(float mx, float my, int button, float dx, float dy) {
		if (ui.mouseDragged(mx, my, button)) return true;
		if (handle >= 0 && selected != null) {
			scaleFromHandle(mx, my);
			return true;
		}
		if (dragging == null) return false;
		float[] p = snapped(dragging, mx - grabX, my - grabY);
		dragging.moveTo(p[0], p[1], width, height);
		return true;
	}

	@Override
	public boolean mouseReleased(float mx, float my, int button) {
		if (ui.mouseReleased(mx, my, button)) return true;
		if (dragging != null) {
			float[] p = snapped(dragging, mx - grabX, my - grabY);
			dragging.moveTo(p[0], p[1], width, height);
		}
		dragging = null;
		handle = -1;
		guideX = guideY = null;
		return true;
	}

	@Override
	public boolean mouseScrolled(float mx, float my, float amount) {
		if (ui.mouseScrolled(mx, my, amount)) return true;
		HudModule m = moduleAt(mx, my);
		if (m == null) m = selected;
		if (m == null || amount == 0) return false;
		float cx = m.x(width) + m.width() / 2, cy = m.y(height) + m.height() / 2;
		setScale(m, m.scale.value() + (amount > 0 ? 0.05 : -0.05));
		m.moveTo(cx - m.width() / 2, cy - m.height() / 2, width, height);
		selected = m;
		return true;
	}

	private void setScale(HudModule m, double s) {
		m.scale.set(Math.max(0.5, Math.min(2.0, Math.round(s * 20) / 20.0)));
		m.layout(game(), true);
	}

	private void scaleFromHandle(float mx, float my) {
		HudModule m = selected;
		float s = m.scale.floatValue(), bw = m.width() / s, bh = m.height() / s;
		if (bw <= 0 || bh <= 0) return;
		setScale(m, Math.max(Math.abs(mx - fixedX) / bw, Math.abs(my - fixedY) / bh));
		float x = handle % 2 == 0 ? fixedX - m.width() : fixedX, y = handle < 2 ? fixedY - m.height() : fixedY;
		m.moveTo(x, y, width, height);
	}

	/** Snaps the module's edges/center to screen insets, the screen center, other modules and the grid. */
	float[] snapped(HudModule m, float x, float y) {
		float w = m.width(), h = m.height();
		guideX = guideY = null;
		Platform p = platform();
		boolean alt = p != null && (p.isKeyDown(Keys.LEFT_ALT) || p.isKeyDown(Keys.RIGHT_ALT));
		if (!snap || alt) return new float[]{x, y};
		List<Float> xs = new ArrayList<Float>(), ys = new ArrayList<Float>();
		xs.add(EDGE);
		xs.add(width / 2f);
		xs.add(width - EDGE);
		ys.add(EDGE);
		ys.add(height / 2f);
		ys.add(height - EDGE);
		for (HudModule o : modules()) {
			if (o == m || o.width() <= 0) continue;
			float ox = o.x(width), oy = o.y(height);
			xs.add(ox);
			xs.add(ox + o.width() / 2);
			xs.add(ox + o.width());
			ys.add(oy);
			ys.add(oy + o.height() / 2);
			ys.add(oy + o.height());
		}
		float[] sx = best(x, w, xs), sy = best(y, h, ys);
		if (sx != null) {
			x = sx[0];
			guideX = sx[1];
		} else if (grid) {
			x = Math.round(x / 8) * 8;
		}
		if (sy != null) {
			y = sy[0];
			guideY = sy[1];
		} else if (grid) {
			y = Math.round(y / 8) * 8;
		}
		return new float[]{x, y};
	}

	/** {snapped start, guide line} for the closest of start/center/end within SNAP of a line, or null. */
	private static float[] best(float start, float size, List<Float> lines) {
		float bestD = SNAP + 0.001f;
		float[] out = null;
		float[] refs = {0, size / 2, size};
		for (float line : lines) {
			for (float ref : refs) {
				float d = Math.abs(start + ref - line);
				if (d < bestD) {
					bestD = d;
					out = new float[]{line - ref, line};
				}
			}
		}
		return out;
	}

	@Override
	public boolean keyPressed(int key, int mods) {
		if (resetSheet.isOpen()) return resetSheet.keyPressed(key, mods) || key == Keys.ESCAPE;
		if (ui.keyPressed(key, mods)) return true;
		if (selected == null) return false;
		float step = (mods & 1) != 0 ? 10 : 1, dx = 0, dy = 0;
		if (key == Keys.LEFT) dx = -step;
		else if (key == Keys.RIGHT) dx = step;
		else if (key == Keys.UP) dy = -step;
		else if (key == Keys.DOWN) dy = step;
		else return false;
		selected.moveTo(selected.x(width) + dx, selected.y(height) + dy, width, height);
		return true;
	}

	private static final class ResetSheet extends Sheet {
		private final Button cancel, reset;

		ResetSheet(Ui ui) {
			super(ui);
			maxWidth = 420;
			cancel = new Button("Cancel", null, Button.Size.REGULAR, Button.Style.SECONDARY, new Runnable() {
				public void run() {
					close();
				}
			});
			reset = new Button("Reset All", null, Button.Size.REGULAR, Button.Style.DESTRUCTIVE_CONFIRM, new Runnable() {
				public void run() {
					confirm();
				}
			});
		}

		@Override
		protected String title() {
			return "Reset HUD Layout?";
		}

		@Override
		protected String glyph() {
			return "arrow.counterclockwise";
		}

		@Override
		protected float bodyHeight(float width) {
			return Gfx.wrap(TEXT, body, width, 3).size() * Gfx.lineHeight(body);
		}

		private static final String TEXT = "Every HUD mod goes back to its default position and size. This can't be undone.";

		@Override
		protected void renderBody(Renderer r, float x, float y, float width, float mx, float my) {
			for (String l : Gfx.wrap(TEXT, body, width, 3)) {
				Gfx.text(r, l, x, y, body, label2);
				y += Gfx.lineHeight(body);
			}
		}

		@Override
		protected void renderFooter(Renderer r, float left, float right, float cy, float mx, float my) {
			float rw = Math.max(u(80), reset.prefW()), cw = Math.max(u(80), cancel.prefW());
			reset.at(right - rw, cy - u(14), rw, u(28));
			cancel.at(reset.x - u(8) - cw, cy - u(14), cw, u(28));
			ui.add(cancel).render(r, mx, my);
			ui.add(reset).render(r, mx, my);
		}

		@Override
		protected void confirm() {
			for (HudModule m : modules()) {
				m.anchor.reset();
				m.offsetX.reset();
				m.offsetY.reset();
				m.scale.reset();
			}
			close();
		}
	}
}
