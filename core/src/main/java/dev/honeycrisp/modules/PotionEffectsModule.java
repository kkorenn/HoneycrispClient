package dev.honeycrisp.modules;

import dev.honeycrisp.module.Anchor;
import dev.honeycrisp.module.BooleanSetting;
import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.HudModule;
import dev.honeycrisp.platform.GameInfo;
import dev.honeycrisp.platform.Renderer;

import java.util.ArrayList;
import java.util.List;

public final class PotionEffectsModule extends HudModule {
	private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
	public final BooleanSetting showName = setting(new BooleanSetting("names", "Show Names", true));
	public final BooleanSetting blink = setting(new BooleanSetting("blink", "Blink When Ending", true));

	private final List<GameInfo.EffectInfo> effects = new ArrayList<GameInfo.EffectInfo>();
	private float row;

	public PotionEffectsModule() {
		super("potions", "Potion Effects", "Active effects with time left", Category.HUD, "flask", Anchor.MIDDLE_RIGHT, -4, 0);
	}

	@Override
	protected void measure(GameInfo game) {
		effects.clear();
		float lh = lineHeight();
		row = Math.max(18, showName.on() ? 2 * lh : lh) + 2;
		float w = 0;
		if (preview) {
			w = Math.max(textWidth(title("Speed", 1)), textWidth("1:30"));
			size(PAD * 2 + 22 + w, PAD * 2 + 2 * row - 2);
			return;
		}
		List<GameInfo.EffectInfo> list = game.effects();
		if (list != null) effects.addAll(list);
		for (GameInfo.EffectInfo e : effects) {
			w = Math.max(w, textWidth(time(e.secondsLeft())));
			if (showName.on()) w = Math.max(w, textWidth(title(e.name(), e.amplifier())));
		}
		if (effects.isEmpty()) size(0, 0);
		else size(PAD * 2 + 22 + w, PAD * 2 + effects.size() * row - 2);
	}

	@Override
	protected void render(Renderer renderer, GameInfo game, float partialTick) {
		int c = color(), dim = (c & 0xFFFFFF) | ((c >>> 24) * 2 / 3 << 24);
		float lh = lineHeight();
		if (preview) {
			icon("hare.fill", PAD, PAD, 18, c);
			line("Speed", 1, 90, PAD, c, dim, lh);
			icon("flame.fill", PAD, PAD + row, 18, c);
			line("Fire Resistance", 0, 8, PAD + row, c, dim, lh);
			return;
		}
		boolean blinkOff = blink.on() && (System.currentTimeMillis() / 250) % 2 == 0;
		for (int i = 0; i < effects.size(); i++) {
			GameInfo.EffectInfo e = effects.get(i);
			float y = PAD + i * row;
			effectIcon(e, PAD, y);
			if (blinkOff && e.secondsLeft() <= 10) continue;
			line(e.name(), e.amplifier(), e.secondsLeft(), y, e.beneficial() ? c : 0xFFFF7777, dim, lh);
		}
	}

	private void line(String name, int amp, int secs, float y, int c, int dim, float lh) {
		int timeColor = secs >= 0 && secs < 10 ? dev.honeycrisp.ui.widgets.Theme.red(dev.honeycrisp.ui.widgets.Theme.dark) : showName.on() ? dim : c;
		if (showName.on()) {
			text(title(name, amp), PAD + 22, y + row / 2 - lh - 1, c);
			text(time(secs), PAD + 22, y + row / 2, timeColor);
		} else {
			text(time(secs), PAD + 22, y + (18 - lh) / 2, timeColor);
		}
	}

	static String title(String name, int amplifier) {
		if (amplifier <= 0) return name;
		return name + " " + (amplifier < ROMAN.length ? ROMAN[amplifier] : Integer.toString(amplifier + 1));
	}

	static String time(int seconds) {
		if (seconds < 0) return "**:**";
		if (seconds >= 3600) return seconds / 3600 + ":" + pad2(seconds / 60 % 60) + ":" + pad2(seconds % 60);
		return seconds / 60 + ":" + pad2(seconds % 60);
	}

	private static String pad2(int v) {
		return v < 10 ? "0" + v : Integer.toString(v);
	}
}
