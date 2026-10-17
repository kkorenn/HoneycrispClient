package dev.honeycrisp.v1_21_11;

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
		mc().setScreen(new SelectWorldScreen(mc().screen));
	}

	@Override
	public void multiplayer() {
		Minecraft mc = mc();
		mc.setScreen(mc.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(mc.screen) : new SafetyScreen(mc.screen));
	}

	@Override
	public void realms() {
		mc().setScreen(new RealmsMainScreen(mc().screen));
	}

	@Override
	public void options() {
		mc().setScreen(new OptionsScreen(mc().screen, mc().options));
	}

	@Override
	public void language() {
		mc().setScreen(new LanguageSelectScreen(mc().screen, mc().options, mc().getLanguageManager()));
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
