package dev.honeycrisp.modules;

import dev.honeycrisp.module.Anchor;
import dev.honeycrisp.module.Category;
import dev.honeycrisp.module.TextHudModule;
import dev.honeycrisp.platform.GameInfo;

public final class ServerAddressModule extends TextHudModule {
	public ServerAddressModule() {
		super("server", "Server Address", "The server you are playing on", Category.HUD, "server.rack", Anchor.TOP_CENTER, 0, 4);
	}

	@Override
	protected String text(GameInfo game) {
		String s = game.server();
		return s == null || s.isEmpty() ? "Singleplayer" : s;
	}

	@Override
	protected String previewText() {
		return "play.example.net";
	}
}
