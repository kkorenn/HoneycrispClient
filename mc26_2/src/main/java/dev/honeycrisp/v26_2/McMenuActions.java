package dev.honeycrisp.v26_2;

import dev.honeycrisp.ui.screens.MenuActions;
import com.mojang.realmsclient.RealmsMainScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

/** Same screens TitleScreen opens; the Honeycrisp Client main menu (current screen) is their parent. */
public final class McMenuActions implements MenuActions {
	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	@Override
	public void singleplayer() {
		mc().gui.setScreen(new SelectWorldScreen(mc().gui.screen()));
	}

	@Override
	public void multiplayer() {
		Minecraft mc = mc();
		mc.gui.setScreen(mc.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(mc.gui.screen()) : new SafetyScreen(mc.gui.screen()));
	}

	@Override
	public void realms() {
		mc().gui.setScreen(new RealmsMainScreen(mc().gui.screen()));
	}

	@Override
	public void options() {
		mc().gui.setScreen(new OptionsScreen(mc().gui.screen(), mc().options, false));
	}

	@Override
	public void language() {
		mc().gui.setScreen(new LanguageSelectScreen(mc().gui.screen(), mc().options, mc().getLanguageManager()));
	}

	@Override
	public void quit() {
		mc().stop();
	}

	@Override
	public String copyright() {
		return Component.translatable("title.credits").getString();
	}
}
