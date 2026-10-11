package dev.honeycrisp.v26_2;

import net.fabricmc.api.ClientModInitializer;

/** Honeycrisp.init runs from MinecraftMixin once the window and GPU device exist; nothing to do this early. */
public class HoneycrispMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
	}
}
