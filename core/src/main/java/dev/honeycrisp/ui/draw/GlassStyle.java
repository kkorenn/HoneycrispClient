package dev.honeycrisp.ui.draw;

/**
 * A translucent material for {@link Draw#glass}. Colors are ARGB; lengths in GUI px. Presets are dark-appearance
 * defaults; retune them here (DESIGN.md has the spec values).
 */
public final class GlassStyle {
	/** Tint over the backdrop blur. */
	public final int tint;
	/** Tint used when the renderer can't blur (more opaque so text stays legible). */
	public final int fallbackTint;
	/** Backdrop blur radius; 0 = never blur. */
	public final float blurRadius;
	/** Inner hairline edge (one framebuffer pixel wide); 0 alpha = none. */
	public final int highlight;
	public final int shadowColor;
	public final float shadowBlur;
	public final float shadowOffsetY;

	public static final GlassStyle WINDOW = new GlassStyle(0xB31C1C1F, 0xEB1E1E21, 12, 0x2EFFFFFF, 0x8C000000, 14, 5);
	public static final GlassStyle SIDEBAR = new GlassStyle(0x80262629, 0xD9242427, 16, 0x1AFFFFFF, 0, 0, 0);
	public static final GlassStyle CONTROL = new GlassStyle(0x33FFFFFF, 0x38FFFFFF, 0, 0x1FFFFFFF, 0x40000000, 2, 0.5f);
	public static final GlassStyle HUD = new GlassStyle(0x66000000, 0x66000000, 0, 0, 0, 0, 0);

	public GlassStyle(int tint, int fallbackTint, float blurRadius, int highlight, int shadowColor, float shadowBlur, float shadowOffsetY) {
		this.tint = tint;
		this.fallbackTint = fallbackTint;
		this.blurRadius = blurRadius;
		this.highlight = highlight;
		this.shadowColor = shadowColor;
		this.shadowBlur = shadowBlur;
		this.shadowOffsetY = shadowOffsetY;
	}

	/** Same material with another tint (fallback tint keeps its own alpha, takes the new color). */
	public GlassStyle withTint(int argb) {
		return new GlassStyle(argb, (fallbackTint & 0xFF000000) | (argb & 0xFFFFFF), blurRadius, highlight, shadowColor, shadowBlur, shadowOffsetY);
	}
}
