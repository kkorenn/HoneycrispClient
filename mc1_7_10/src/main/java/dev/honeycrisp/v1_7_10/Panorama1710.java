package dev.honeycrisp.v1_7_10;

import net.minecraft.client.gui.GuiMainMenu;

import java.lang.reflect.Method;

/** Vanilla's animated, resource-pack-aware panorama, without its logo or menu controls. */
final class Panorama1710 extends GuiMainMenu {
	private static final Method SKYBOX = find();

	private static Method find() {
		for (String name : new String[] {"func_73971_c", "renderSkybox"}) {
			try {
				Method m = GuiMainMenu.class.getDeclaredMethod(name, int.class, int.class, float.class);
				m.setAccessible(true);
				return m;
			} catch (NoSuchMethodException ignored) {
			}
		}
		return null;
	}

	/** False when the skybox method could not be found; the caller then draws the default background. */
	static boolean available() {
		return SKYBOX != null;
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		try {
			SKYBOX.invoke(this, mouseX, mouseY, partialTicks);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not render the Minecraft panorama", e);
		}
	}
}
