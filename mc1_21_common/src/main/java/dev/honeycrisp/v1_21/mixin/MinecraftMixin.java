package dev.honeycrisp.v1_21.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Hooks;
import dev.honeycrisp.v1_21.McPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Unique
	private static final int VANILLA_HURT = 0xB3FF0000;
	@Unique
	private static int appliedHitColor = VANILLA_HURT;

	@Shadow
	public net.minecraft.client.multiplayer.ClientLevel level;

	/** Window, GPU device and Gui exist at the end of the constructor. */
	@Inject(method = "<init>", at = @At("TAIL"))
	private void honeycrisp$init(CallbackInfo ci) {
		try {
			Honeycrisp.init(McPlatform.INSTANCE);
		} catch (Throwable t) {
			LoggerFactory.getLogger("Honeycrisp Client").error("Honeycrisp Client failed to initialize; running vanilla", t);
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void honeycrisp$tick(CallbackInfo ci) {
		Hooks h = Honeycrisp.hooks();
		if (h == null) return;
		h.onClientTick();
		honeycrisp$applyHitColor(h.hitColor());
	}

	@Inject(method = "startAttack", at = @At("HEAD"))
	private void honeycrisp$attack(CallbackInfoReturnable<Boolean> cir) {
		Hooks h = Honeycrisp.hooks();
		if (h != null) h.onAttack();
	}

	/** setScreen(null) without a level falls back to a new TitleScreen, so that case is replaced too. */
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void honeycrisp$replaceTitle(Screen screen, CallbackInfo ci) {
		Hooks h = Honeycrisp.hooks();
		if (h == null || !(screen instanceof TitleScreen || screen == null && level == null)) return;
		if (h.replaceMainMenu()) {
			ci.cancel();
			h.openMainMenu();
		}
	}

	/** Recolors the entity hurt overlay (OverlayTexture rows 0–7); 0 restores vanilla red. */
	@Unique
	private static void honeycrisp$applyHitColor(int argb) {
		int color = argb == 0 ? VANILLA_HURT : argb;
		if (color == appliedHitColor) return;
		appliedHitColor = color;
		DynamicTexture t = ((OverlayTextureAccessor) Minecraft.getInstance().gameRenderer.overlayTexture()).honeycrisp$texture();
		NativeImage pixels = t.getPixels();
		for (int y = 0; y < 8; y++)
			for (int x = 0; x < 16; x++)
				pixels.setPixel(x, y, color);
		t.upload();
	}
}
