package dev.honeycrisp.v1_12_2;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.platform.Renderer;
import dev.honeycrisp.ui.widgets.Gfx;
import dev.honeycrisp.ui.widgets.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiButtonLanguage;
import net.minecraft.client.gui.GuiLockIconButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.config.GuiUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Replaces only control artwork: vanilla still owns hit testing, dragging, values, labels and actions. */
public final class ControlGlass1122 {
	private ControlGlass1122() {
	}

	public static void buttonPart(GuiButton button, int x, int y, int u, int v, int w, int h) {
		if (Honeycrisp.hooks() == null) {
			button.drawTexturedModalRect(x, y, u, v, w, h);
			return;
		}
		if (u == 0) body(button);
	}

	public static void sliderPart(GuiButton button, int x, int y, int u, int v, int w, int h) {
		if (Honeycrisp.hooks() == null) {
			button.drawTexturedModalRect(x, y, u, v, w, h);
			return;
		}
		if (u == 0) thumb(button, x, 8);
	}

	public static void iconPart(GuiButton button, int x, int y, int u, int v, int w, int h) {
		if (Honeycrisp.hooks() == null) {
			button.drawTexturedModalRect(x, y, u, v, w, h);
			return;
		}
		body(button);
		Renderer r = Honeycrisp.platform().renderer();
		String icon = button instanceof GuiButtonLanguage ? "globe"
			: button instanceof GuiLockIconButton ? (((GuiLockIconButton)button).isLocked() ? "lock.fill" : "lock.open.fill") : null;
		if (icon != null) {
			Gfx.iconAt(r, icon, button.x + button.width / 2F, button.y + button.height / 2F,
				Math.min(button.width, button.height) * 0.65F, button.enabled ? 0xF2FFFFFF : 0x80FFFFFF);
		} else {
			// Keep custom artwork (recipe, page, merchant and image buttons) recognizable.
			button.drawTexturedModalRect(x, y, u, v, w, h);
		}
	}

	public static void forgeBox(ResourceLocation texture, int x, int y, int u, int v, int w, int h,
		int textureW, int textureH, int top, int bottom, int left, int right, float z, GuiButton button) {
		if (Honeycrisp.hooks() == null) {
			GuiUtils.drawContinuousTexturedBox(texture, x, y, u, v, w, h, textureW, textureH, top, bottom, left, right, z);
		} else if (w == 8) {
			thumb(button, x, w);
		} else {
			body(button);
		}
	}

	private static boolean hovered(GuiButton button) {
		Minecraft mc = Minecraft.getMinecraft();
		ScaledResolution resolution = new ScaledResolution(mc);
		float x = Mouse.getX() * resolution.getScaledWidth() / (float)mc.displayWidth;
		float y = resolution.getScaledHeight() - Mouse.getY() * resolution.getScaledHeight() / (float)mc.displayHeight - 1;
		return button.enabled && x >= button.x && x < button.x + button.width
			&& y >= button.y && y < button.y + button.height;
	}

	private static void body(GuiButton button) {
		Theme.frame();
		int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		Renderer r = Honeycrisp.platform().renderer();
		float radius = Math.min(8, button.height / 2F);
		float opacity = Gfx.opacity;
		try {
			Gfx.opacity = button.enabled ? 1 : 0.45F;
			Gfx.clearGlass(r, button.x, button.y, button.width, button.height, radius);
			if (hovered(button)) {
				Gfx.rect(r, button.x, button.y, button.width, button.height, radius,
					Mouse.isButtonDown(0) ? 0x30FFFFFF : 0x18FFFFFF);
				Gfx.stroke(r, button.x, button.y, button.width, button.height, radius, 1, 0x70FFFFFF);
			}
		} finally {
			Gfx.opacity = opacity;
			GlStateManager.bindTexture(texture);
		}
	}

	private static void thumb(GuiButton button, int x, int w) {
		Theme.frame();
		int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		Renderer r = Honeycrisp.platform().renderer();
		float opacity = Gfx.opacity;
		try {
			Gfx.opacity = button.enabled ? 1 : 0.45F;
			float cy = button.y + button.height - 3;
			Gfx.capsule(r, button.x + 4, cy, Math.max(0, button.width - 8), 2, 0x33FFFFFF);
			Gfx.capsule(r, button.x + 4, cy, Math.max(0, x - button.x), 2, 0xCCFFFFFF);
			Gfx.controlGlass(r, x, button.y + 2, w, Math.max(4, button.height - 4), w / 2F);
		} finally {
			Gfx.opacity = opacity;
			GlStateManager.bindTexture(texture);
		}
	}
}
