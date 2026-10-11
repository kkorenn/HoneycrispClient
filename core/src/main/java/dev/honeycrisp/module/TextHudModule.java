package dev.honeycrisp.module;

import dev.honeycrisp.platform.GameInfo;
import dev.honeycrisp.platform.Renderer;

/** A HUD element that is just one or more lines of text ('\n'-separated). */
public abstract class TextHudModule extends HudModule {
	private String[] lines = new String[0];

	protected TextHudModule(String id, String name, String description, Category category, String sfSymbol,
			Anchor anchor, float offsetX, float offsetY) {
		super(id, name, description, category, sfSymbol, anchor, offsetX, offsetY);
	}

	/** Current text, or null/"" to hide. */
	protected abstract String text(GameInfo game);

	/** Sample text for the HUD editor outside a world. */
	protected abstract String previewText();

	@Override
	protected void measure(GameInfo game) {
		String t = preview ? previewText() : text(game);
		if (t == null || t.isEmpty()) {
			lines = new String[0];
			size(0, 0);
			return;
		}
		lines = t.split("\n");
		float w = 0;
		for (String l : lines) w = Math.max(w, textWidth(l));
		size(w + 2 * PAD, lines.length * lineHeight() + 2 * PAD);
	}

	@Override
	protected void render(Renderer renderer, GameInfo game, float partialTick) {
		int c = color();
		float lh = lineHeight();
		for (int k = 0; k < lines.length; k++) text(lines[k], alignX(textWidth(lines[k])), PAD + k * lh, c);
	}
}
