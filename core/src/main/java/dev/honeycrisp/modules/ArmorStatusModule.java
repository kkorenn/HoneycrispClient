package dev.honeycrisp.modules;

import dev.honeycrisp.module.Anchor;
import dev.honeycrisp.module.BooleanSetting;
import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.ChoiceSetting;
import dev.honeycrisp.module.HudModule;
import dev.honeycrisp.platform.GameInfo;
import dev.honeycrisp.platform.Renderer;

public final class ArmorStatusModule extends HudModule {
	public enum Durability {
		PERCENT("Percent"), VALUE("Value"), NONE("Hidden");

		private final String label;

		Durability(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private static final float ROW = 18;
	private static final String[] PREVIEW_SYMBOLS = {"shield.fill", "tshirt.fill", "figure.stand", "shoe.fill", "hammer.fill"};
	private static final String[] PREVIEW_TEXT = {"100%", "86%", "73%", "45%", "1561"};

	public final ChoiceSetting<Durability> durability = setting(new ChoiceSetting<Durability>("durability", "Durability", Durability.PERCENT));
	public final BooleanSetting heldItem = setting(new BooleanSetting("held", "Show Held Item", true));
	public final BooleanSetting colorByDamage = setting(new BooleanSetting("damageColor", "Color by Durability", true));

	private final GameInfo.ItemInfo[] items = new GameInfo.ItemInfo[5];
	private final String[] texts = new String[5];
	private final int[] colors = new int[5];
	private int rows;

	public ArmorStatusModule() {
		super("armor", "Armor Status", "Armor and held item durability", Category.HUD, "shield.lefthalf.filled", Anchor.BOTTOM_RIGHT, -4, -4);
	}

	@Override
	protected void measure(GameInfo game) {
		rows = 0;
		float w = 0;
		int n = heldItem.on() ? 5 : 4;
		GameInfo.ItemInfo[] armor = preview ? null : game.armor();
		for (int i = 0; i < n; i++) {
			GameInfo.ItemInfo it = preview ? null : i < 4 ? (armor != null && armor.length > i ? armor[i] : null) : game.heldItem();
			if (!preview && it == null) continue;
			items[rows] = it;
			texts[rows] = preview ? (durability.get() == Durability.NONE ? "" : PREVIEW_TEXT[i]) : label(it);
			colors[rows] = preview ? (i == 3 ? 0xFFFFDD55 : 0xFF55FF55) : damageColor(it);
			w = Math.max(w, textWidth(texts[rows]));
			rows++;
		}
		if (rows == 0) size(0, 0);
		else size(PAD * 2 + 16 + (w > 0 ? 4 + w : 0), PAD * 2 + rows * ROW - 2);
	}

	private String label(GameInfo.ItemInfo it) {
		int max = it.maxDurability(), cur = it.durability();
		if (cur < 0 || max <= 0) return it.count() > 1 ? Integer.toString(it.count()) : "";
		switch (durability.get()) {
			case PERCENT: return Math.round(100f * cur / max) + "%";
			case VALUE: return Integer.toString(cur);
			default: return "";
		}
	}

	private int damageColor(GameInfo.ItemInfo it) {
		int max = it.maxDurability(), cur = it.durability();
		if (!colorByDamage.on() || cur < 0 || max <= 0) return color();
		float f = (float) cur / max;
		return f > 0.5f ? 0xFF55FF55 : f > 0.25f ? 0xFFFFDD55 : 0xFFFF5555;
	}

	@Override
	protected void render(Renderer renderer, GameInfo game, float partialTick) {
		float lh = lineHeight();
		for (int i = 0; i < rows; i++) {
			float y = PAD + i * ROW;
			if (items[i] != null) itemIcon(items[i], PAD, y);
			else icon(PREVIEW_SYMBOLS[i], PAD, y, 16, color());
			if (!texts[i].isEmpty()) text(texts[i], PAD + 20, y + (16 - lh) / 2, colorByDamage.on() ? colors[i] : color());
		}
	}
}
