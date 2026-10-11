package dev.honeycrisp.ui.draw;

/**
 * SF Pro (or SF Mono) rendered at runtime through CoreText. Size is in GUI-scaled px, weight 100–900
 * (400 regular, 500 medium, 600 semibold, 700 bold).
 * <p>
 * Presets are the macOS/iOS text styles divided by 2, so at guiScale 2 one GUI px is one point
 * (LARGE_TITLE 34pt → 17, BODY 13pt → 6.5, CAPTION2 8.5pt → 4.25). HUD/HUD_BOLD are compact in-game styles.
 */
public final class Font {
	public static final Font LARGE_TITLE = of(17, 400);
	public static final Font TITLE = of(14, 400);
	public static final Font TITLE2 = of(11, 400);
	public static final Font TITLE3 = of(10, 400);
	public static final Font HEADLINE = of(6.5f, 600);
	public static final Font BODY = of(6.5f, 400);
	public static final Font CALLOUT = of(6, 400);
	public static final Font SUBHEADLINE = of(5.5f, 400);
	public static final Font FOOTNOTE = of(5, 400);
	public static final Font CAPTION = of(4.5f, 400);
	public static final Font CAPTION2 = of(4.25f, 400);
	public static final Font HUD = of(5.5f, 500);
	public static final Font HUD_BOLD = of(5.5f, 600);

	public final float size;
	public final int weight;
	public final boolean mono;
	final String key;

	private Font(float size, int weight, boolean mono) {
		this.size = size;
		this.weight = Math.max(100, Math.min(900, weight));
		this.mono = mono;
		this.key = size + "/" + this.weight + (mono ? "m" : "");
	}

	public static Font of(float size, int weight) {
		return new Font(size, weight, false);
	}

	public static Font mono(float size, int weight) {
		return new Font(size, weight, true);
	}

	public Font withWeight(int weight) {
		return new Font(size, weight, mono);
	}

	public Font withSize(float size) {
		return new Font(size, weight, mono);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Font && ((Font) o).key.equals(key);
	}

	@Override
	public int hashCode() {
		return key.hashCode();
	}

	@Override
	public String toString() {
		return "Font[" + key + "]";
	}
}
