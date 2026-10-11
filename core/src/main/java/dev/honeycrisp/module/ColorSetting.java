package dev.honeycrisp.module;

import java.util.LinkedHashMap;
import java.util.Map;

/** ARGB color with an optional chroma (rainbow) mode that cycles the hue and keeps the alpha. */
public class ColorSetting extends Setting<Integer> {
	public static final long CHROMA_PERIOD_MS = 4000;
	private boolean chroma;

	public ColorSetting(String id, String name, int defaultArgb) {
		super(id, name, defaultArgb);
	}

	/** The color to draw with right now. */
	public int argb() {
		return chroma ? chroma(System.currentTimeMillis(), value >>> 24) : value;
	}

	/** The stored color, ignoring chroma. */
	public int rawArgb() {
		return value;
	}

	public boolean chroma() {
		return chroma;
	}

	public void setChroma(boolean chroma) {
		if (this.chroma == chroma) return;
		this.chroma = chroma;
		changed();
	}

	@Override
	public void reset() {
		setChroma(false);
		super.reset();
	}

	public static int chroma(long timeMillis, int alpha) {
		return (alpha << 24) | hsv((timeMillis % CHROMA_PERIOD_MS) / (float) CHROMA_PERIOD_MS, 0.8f, 1f);
	}

	/** HSV (all 0..1) → RGB 0xRRGGBB. Not java.awt.Color: AWT must stay unloaded in the game on macOS. */
	public static int hsv(float h, float s, float v) {
		float hh = (h - (float) Math.floor(h)) * 6f;
		int i = (int) hh;
		float f = hh - i, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
		float r, g, b;
		switch (i) {
			case 0: r = v; g = t; b = p; break;
			case 1: r = q; g = v; b = p; break;
			case 2: r = p; g = v; b = t; break;
			case 3: r = p; g = q; b = v; break;
			case 4: r = t; g = p; b = v; break;
			default: r = v; g = p; b = q; break;
		}
		return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
	}

	@Override
	public Object toJson() {
		Map<String, Object> m = new LinkedHashMap<String, Object>();
		m.put("argb", String.format("#%08X", value));
		m.put("chroma", chroma);
		return m;
	}

	@Override
	public void fromJson(Object json) {
		if (json instanceof Number) set(((Number) json).intValue());
		if (!(json instanceof Map)) return;
		Map<?, ?> m = (Map<?, ?>) json;
		Object c = m.get("argb");
		if (c instanceof String && ((String) c).startsWith("#")) {
			try {
				set((int) Long.parseLong(((String) c).substring(1), 16));
			} catch (NumberFormatException ignored) {
			}
		} else if (c instanceof Number) {
			set(((Number) c).intValue());
		}
		if (m.get("chroma") instanceof Boolean) setChroma((Boolean) m.get("chroma"));
	}
}
