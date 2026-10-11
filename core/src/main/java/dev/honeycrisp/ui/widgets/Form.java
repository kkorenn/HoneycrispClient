package dev.honeycrisp.ui.widgets;

import dev.honeycrisp.module.BooleanSetting;
import dev.honeycrisp.module.ChoiceSetting;
import dev.honeycrisp.module.ColorSetting;
import dev.honeycrisp.module.KeybindSetting;
import dev.honeycrisp.module.NumberSetting;
import dev.honeycrisp.module.Setting;
import dev.honeycrisp.module.TextSetting;
import dev.honeycrisp.platform.Renderer;

import java.util.ArrayList;
import java.util.List;

import static dev.honeycrisp.ui.widgets.Theme.*;

/**
 * Grouped form (3.11/3.12): titled L2 groups of 36u rows (48u with a detail line), each with trailing controls.
 * Build it once (controls keep their animation state), then {@link #height} and {@link #render} every frame.
 */
public final class Form {
	public static final class Row {
		public final String label;
		public String detail, glyph, value;
		public Widget[] controls = new Widget[0];
		/** Navigation rows: the whole row is a button with a chevron. */
		public Runnable action;
		public boolean visible = true;
		private final RowHit hit = new RowHit(this);

		Row(String label) {
			this.label = label;
		}

		public Row detail(String d) {
			detail = d;
			return this;
		}

		public Row glyph(String g) {
			glyph = g;
			return this;
		}
	}

	public static final class Group {
		public final String title;
		public String footer;
		public final List<Row> rows = new ArrayList<Row>();

		Group(String title) {
			this.title = title;
		}

		public Row row(String label, Widget... controls) {
			Row r = new Row(label);
			r.controls = controls;
			rows.add(r);
			return r;
		}

		public Row value(String label, String value) {
			Row r = row(label);
			r.value = value;
			return r;
		}

		public Row link(String label, Runnable action) {
			Row r = row(label);
			r.action = action;
			return r;
		}
	}

	public final List<Group> groups = new ArrayList<Group>();

	public Group group(String title) {
		Group g = new Group(title);
		groups.add(g);
		return g;
	}

	/** Group, row and controls for a setting; null for hidden settings. */
	public static Widget[] controls(Ui ui, Setting<?> s) {
		if (s instanceof BooleanSetting) return new Widget[]{new Toggle(Value.of((BooleanSetting) s), true)};
		if (s instanceof NumberSetting) return new Widget[]{new Slider((NumberSetting) s)};
		if (s instanceof ChoiceSetting) {
			ChoiceSetting<?> c = (ChoiceSetting<?>) s;
			if (c.choices().length <= 4) return new Widget[]{Segmented.of(c)};
			Object[] ch = c.choices();
			String[] labels = new String[ch.length];
			for (int i = 0; i < ch.length; i++) labels[i] = ch[i].toString();
			return new Widget[]{new PopUpButton(ui, indexOf(c), labels)};
		}
		if (s instanceof ColorSetting) return new Widget[]{new ColorWell(ui, (ColorSetting) s)};
		if (s instanceof KeybindSetting) {
			final KeybindSetting k = (KeybindSetting) s;
			Segmented mode = new Segmented(new Value<Integer>() {
				public Integer get() {
					return k.mode().ordinal();
				}

				public void set(Integer v) {
					k.setMode(KeybindSetting.Mode.values()[v]);
				}
			}, "Hold", "Toggle");
			return new Widget[]{mode, new KeybindButton(k)};
		}
		if (s instanceof TextSetting) {
			TextField f = new TextField(Value.of((TextSetting) s), s.name, false);
			f.maxLength = ((TextSetting) s).maxLength;
			return new Widget[]{f};
		}
		return new Widget[0];
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Value<Integer> indexOf(ChoiceSetting c) {
		return Value.index(c);
	}

	public Row add(Group g, Ui ui, Setting<?> s) {
		Row r = g.row(s.name, controls(ui, s));
		if (!s.description().isEmpty()) r.detail = s.description();
		return r;
	}

	private static float rowH(Row r) {
		return u(r.detail != null ? 48 : 36);
	}

	public float height(float width) {
		float h = 0;
		boolean first = true;
		for (Group g : groups) {
			if (!visible(g)) continue;
			if (g.title != null) h += (first ? 0 : u(20)) + lineHeight(headline) + u(8);
			else if (!first) h += u(24);
			for (Row r : g.rows) if (r.visible) h += rowH(r);
			if (g.footer != null) h += u(6) + Gfx.wrap(g.footer, subheadline, width, 3).size() * lineHeight(subheadline);
			first = false;
		}
		return h;
	}

	private static boolean visible(Group g) {
		for (Row r : g.rows) if (r.visible) return true;
		return false;
	}

	private static float lineHeight(dev.honeycrisp.ui.draw.Font f) {
		return Gfx.lineHeight(f);
	}

	public void render(Renderer r, Ui ui, float x, float y, float w, float mx, float my) {
		boolean first = true;
		for (Group g : groups) {
			if (!visible(g)) continue;
			if (g.title != null) {
				if (!first) y += u(20);
				Gfx.text(r, g.title, x + u(4), y, headline, label);
				y += lineHeight(headline) + u(8);
			} else if (!first) {
				y += u(24);
			}
			first = false;
			float gh = 0;
			for (Row row : g.rows) if (row.visible) gh += rowH(row);
			Gfx.groupGlass(r, x, y, w, gh, u(12), false);
			float ry = y;
			int last = -1;
			for (int i = 0; i < g.rows.size(); i++) if (g.rows.get(i).visible) last = i;
			for (int i = 0; i < g.rows.size(); i++) {
				Row row = g.rows.get(i);
				if (!row.visible) continue;
				float rh = rowH(row);
				renderRow(r, ui, row, x, ry, w, rh, i == last, ry == y, mx, my);
				ry += rh;
			}
			y += gh;
			if (g.footer != null) {
				y += u(6);
				for (String line : Gfx.wrap(g.footer, subheadline, w, 3)) {
					Gfx.text(r, line, x + u(4), y, subheadline, label2);
					y += lineHeight(subheadline);
				}
			}
		}
	}

	private void renderRow(Renderer r, Ui ui, Row row, float x, float y, float w, float h, boolean last, boolean firstRow, float mx, float my) {
		float cy = y + h / 2, lx = x + u(row.glyph != null ? 38 : 12);
		boolean clickable = row.action != null || (row.controls.length == 1 && row.controls[0] instanceof Toggle);
		if (clickable) {
			row.hit.at(x, y, w, h);
			ui.add(row.hit);
			if (row.action != null && row.hit.hovered(mx, my)) {
				r.pushClip(x, y, w, h);
				Gfx.rect(r, x, firstRow ? y : y - u(12), w, h + (firstRow ? 0 : u(12)) + (last ? 0 : u(12)), u(12), fill4);
				r.popClip();
			}
		}
		if (row.glyph != null) Gfx.iconAt(r, row.glyph, x + u(12 + 8), cy, u(16), label2);
		float right = x + w - u(12);
		if (row.action != null) {
			Gfx.iconAt(r, "chevron.right", right - u(5), cy, u(10), label3);
			right -= u(10 + 8);
		}
		if (row.value != null) {
			Gfx.textMidRight(r, row.value, right, cy, body, label2);
			right -= Gfx.width(row.value, body) + u(8);
		}
		for (int i = row.controls.length - 1; i >= 0; i--) {
			Widget c = row.controls[i];
			if (c instanceof Slider) {
				Gfx.textMidRight(r, ((Slider) c).format(), right, cy, callout, label2);
				right -= u(40 + 8);
				c.at(right - u(160), cy - u(12), u(160), u(24));
			} else if (c instanceof TextField) {
				c.at(right - u(160), cy - u(14), u(160), u(28));
			} else {
				float cw = c.prefW(), ch = c.prefH();
				c.at(right - cw, cy - ch / 2, cw, ch);
			}
			right = c.x - u(8);
			ui.add(c).render(r, mx, my);
		}
		float avail = right - lx - u(4);
		if (row.detail != null) {
			Gfx.textMid(r, Gfx.truncate(row.label, body, avail), lx, cy - u(8), body, label);
			Gfx.textMid(r, Gfx.truncate(row.detail, subheadline, avail), lx, cy + u(9), subheadline, label2);
		} else {
			Gfx.textMid(r, Gfx.truncate(row.label, body, avail), lx, cy, body, label);
		}
		if (!last) Gfx.fill(r, lx, y + h - hairline, x + w - lx, hairline, separator);
	}

	/** Makes the whole row toggle its switch, or run its action. */
	private static final class RowHit extends Widget {
		private final Row row;
		private boolean pressed;

		RowHit(Row row) {
			this.row = row;
		}

		@Override
		public void render(Renderer r, float mx, float my) {
		}

		@Override
		public boolean mouseClicked(float mx, float my, int button) {
			pressed = button == 0;
			return pressed;
		}

		@Override
		public void mouseReleased(float mx, float my, int button) {
			if (!pressed || !hit(mx, my)) return;
			pressed = false;
			if (row.action != null) row.action.run();
			else if (row.controls[0].enabled) ((Toggle) row.controls[0]).toggle();
		}
	}
}
