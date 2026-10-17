package dev.honeycrisp.module;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.platform.GameInfo;
import dev.honeycrisp.platform.Platform;
import dev.honeycrisp.platform.Renderer;
import dev.honeycrisp.ui.draw.Draw;
import dev.honeycrisp.ui.draw.Font;
import dev.honeycrisp.ui.screens.UiPrefs;

/**
 * A draggable HUD element. Subclasses {@link #measure} their unscaled content size, then {@link #render} in local
 * unscaled coordinates through the helpers (text, fill, icon...), which apply position and scale.
 * Position = anchor point + offset (GUI px); see {@link Anchor#position}.
 */
public abstract class HudModule extends Module {
	public enum Style {
		PILL("Pill"), SHADOW("Shadow"), PLAIN("Plain");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public static final float PAD = 4;

	public final ChoiceSetting<Anchor> anchor;
	public final NumberSetting offsetX, offsetY;
	public final ChoiceSetting<Style> style = setting(new ChoiceSetting<Style>("style", "Style", Style.PILL));
	public final NumberSetting scale = setting(new NumberSetting("scale", "Scale", 1, 0.5, 3, 0.05));
	public final BooleanSetting background = setting(new BooleanSetting("background", "Background", true));
	public final BooleanSetting outline = setting(new BooleanSetting("outline", "Outline", false));
	public final BooleanSetting shadow = setting(new BooleanSetting("shadow", "Text Shadow", false));
	public final ColorSetting textColor = setting(new ColorSetting("textColor", "Text Color", 0xFFFFFFFF));

	private float width, height;
	/** True while drawn outside a world (HUD editor / menus): show sample data, don't read GameInfo. */
	protected boolean preview;
	private Renderer r;
	private float ox, oy, sc = 1;
	private Font font;
	private float fontScale = -1;

	protected HudModule(String id, String name, String description, Category category, String sfSymbol,
			Anchor anchor, float offsetX, float offsetY) {
		super(id, name, description, category, sfSymbol);
		this.anchor = hidden(new ChoiceSetting<Anchor>("anchor", "Anchor", anchor));
		this.offsetX = hidden(new NumberSetting("x", "X", offsetX, -10000, 10000, 0));
		this.offsetY = hidden(new NumberSetting("y", "Y", offsetY, -10000, 10000, 0));
	}

	/** Sets the content size via {@link #size}; size 0 hides the element this frame. */
	protected abstract void measure(GameInfo game);

	protected abstract void render(Renderer renderer, GameInfo game, float partialTick);

	/** Measures, positions and draws. preview = sample content (HUD editor outside a world). */
	public final void draw(Renderer renderer, GameInfo game, float partialTick, boolean preview, float screenW, float screenH) {
		this.preview = preview;
		measure(game);
		if (width <= 0 || height <= 0) return;
		sc = scale.floatValue();
		ox = x(screenW);
		oy = y(screenH);
		r = renderer;
		try {
			if (background.on() && style.get() == Style.PILL) drawBackground();
			render(renderer, game, partialTick);
		} finally {
			r = null;
		}
	}

	/** Updates the size for the HUD editor without drawing. */
	public final void layout(GameInfo game, boolean preview) {
		this.preview = preview;
		measure(game);
	}

	protected final void size(float w, float h) {
		width = w;
		height = h;
	}

	/** Scaled size in GUI px, from the last draw/layout. */
	public float width() {
		return width * scale.floatValue();
	}

	public float height() {
		return height * scale.floatValue();
	}

	public float x(float screenW) {
		return Anchor.position(anchor.get().fx, offsetX.floatValue(), width(), screenW);
	}

	public float y(float screenH) {
		return Anchor.position(anchor.get().fy, offsetY.floatValue(), height(), screenH);
	}

	/** HUD editor drop: re-anchors to the nearest screen region and stores the offset from it. */
	public void moveTo(float x, float y, float screenW, float screenH) {
		float w = width(), h = height();
		x = Math.max(0, Math.min(x, screenW - w));
		y = Math.max(0, Math.min(y, screenH - h));
		Anchor a = Anchor.nearest(x, y, w, h, screenW, screenH);
		anchor.set(a);
		offsetX.set((double) Math.round(Anchor.offset(a.fx, x, w, screenW) * 2) / 2);
		offsetY.set((double) Math.round(Anchor.offset(a.fy, y, h, screenH) * 2) / 2);
	}

	/** Horizontal alignment of multi-line content, following the anchor (right-anchored text is right-aligned). */
	protected float alignX(float lineWidth) {
		return (width - 2 * PAD - lineWidth) * anchor.get().fx + PAD;
	}

	// ---- drawing helpers (local unscaled coordinates) ----

	protected void drawBackground() {
		if (style.get() != Style.PILL) return;
		float w = width * sc, h = height * sc, rad = 4 * sc;
		boolean rt = UiPrefs.get().reduceTransparency.on();
		Draw.roundRect(r, ox, oy, w, h, rad, rt ? 0xF5121214 : 0x66000000);
		if (outline.on()) {
			Platform p = Honeycrisp.platform();
			float hl = p != null && p.guiScale() > 0 ? (float) (1 / p.guiScale()) : 0.5f;
			Draw.roundRectStroke(r, ox, oy, w, h, rad, hl, 0x1AFFFFFF);
		}
	}

	protected float sx(float x) {
		return ox + x * sc;
	}

	protected float sy(float y) {
		return oy + y * sc;
	}

	protected float scaleFactor() {
		return sc;
	}

	protected Renderer renderer() {
		return r;
	}

	protected int color() {
		return textColor.argb();
	}

	public static float textWidth(String s) {
		return Draw.textWidth(s, Font.HUD);
	}

	public static float lineHeight() {
		return Draw.lineHeight(Font.HUD);
	}

	protected void text(String s, float x, float y, int argb) {
		Font f = scaledFont();
		if (shadow.on() || style.get() == Style.SHADOW) {
			int a = argb >>> 24;
			Draw.text(r, s, sx(x) + 1f * sc, sy(y) + 1f * sc, f, (a * 0x40 / 255) << 24);
			Draw.text(r, s, sx(x) + 0.5f * sc, sy(y) + 0.5f * sc, f, (a * 0x99 / 255) << 24);
		}
		Draw.text(r, s, sx(x), sy(y), f, argb);
	}

	protected void textCentered(String s, float cx, float y, int argb) {
		text(s, cx - textWidth(s) / 2, y, argb);
	}

	protected void fill(float x, float y, float w, float h, int argb) {
		r.fill(sx(x), sy(y), w * sc, h * sc, argb);
	}

	protected void roundRect(float x, float y, float w, float h, float radius, int argb) {
		Draw.roundRect(r, sx(x), sy(y), w * sc, h * sc, radius * sc, argb);
	}

	protected void icon(String sfSymbol, float x, float y, float size, int argb) {
		Draw.icon(r, sfSymbol, sx(x), sy(y), size * sc, argb);
	}

	/** Vanilla item icons are a fixed 16 px; center them in the scaled 16 px slot. */
	protected void itemIcon(GameInfo.ItemInfo item, float x, float y) {
		item.drawIcon(sx(x) + 8 * (sc - 1), sy(y) + 8 * (sc - 1));
	}

	protected void effectIcon(GameInfo.EffectInfo effect, float x, float y) {
		effect.drawIcon(sx(x) + 9 * (sc - 1), sy(y) + 9 * (sc - 1));
	}

	private Font scaledFont() {
		if (fontScale != sc) {
			font = sc == 1 ? Font.HUD : Font.HUD.withSize(Font.HUD.size * sc);
			fontScale = sc;
		}
		return font;
	}
}
