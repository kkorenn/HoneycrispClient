package dev.honeycrisp.v1_12_2;

import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Method;

/** Vanilla's animated, resource-pack-aware panorama, without its logo or menu controls. */
final class Panorama1122 extends GuiMainMenu {
	private static final Method SKYBOX = ReflectionHelper.findMethod(GuiMainMenu.class,
		"renderSkybox", "func_73971_c", int.class, int.class, float.class);

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		try {
			SKYBOX.invoke(this, mouseX, mouseY, partialTicks);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not render the Minecraft panorama", e);
		}
	}
}
