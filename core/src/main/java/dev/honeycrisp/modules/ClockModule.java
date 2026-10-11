package dev.honeycrisp.modules;

import dev.honeycrisp.module.Anchor;
import dev.honeycrisp.module.BooleanSetting;
import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.ChoiceSetting;
import dev.honeycrisp.module.TextHudModule;
import dev.honeycrisp.platform.GameInfo;

import java.util.Calendar;

public final class ClockModule extends TextHudModule {
	public enum Format {
		H24("24-hour"), H12("12-hour");

		private final String label;

		Format(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public final ChoiceSetting<Format> format = setting(new ChoiceSetting<Format>("format", "Format", Format.H24));
	public final BooleanSetting seconds = setting(new BooleanSetting("seconds", "Show Seconds", false));

	public ClockModule() {
		super("clock", "Clock", "Real-world time", Category.HUD, "clock", Anchor.TOP_RIGHT, -4, 4);
	}

	@Override
	protected String text(GameInfo game) {
		Calendar c = Calendar.getInstance();
		return format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND));
	}

	@Override
	protected String previewText() {
		return format(21, 41, 7);
	}

	String format(int h, int m, int s) {
		StringBuilder sb = new StringBuilder();
		boolean twelve = format.get() == Format.H12;
		sb.append(twelve ? (h + 11) % 12 + 1 : h).append(':').append(m < 10 ? "0" : "").append(m);
		if (seconds.on()) sb.append(':').append(s < 10 ? "0" : "").append(s);
		if (twelve) sb.append(h < 12 ? " AM" : " PM");
		return sb.toString();
	}
}
