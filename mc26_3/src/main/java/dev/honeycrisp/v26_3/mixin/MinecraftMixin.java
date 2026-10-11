package dev.honeycrisp.v26_3.mixin;

import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Hooks;
import dev.honeycrisp.ui.screens.MenuActions;
import dev.honeycrisp.v26_3.McMenuActions;
import dev.honeycrisp.v26_3.McPlatform;
import dev.honeycrisp.v26_3.McRenderer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	/** Camera type before freelook forced third person; null while not forcing. */
	private CameraType honeycrisp$savedCamera;

	/** Window, GPU device and Gui exist at the end of the constructor. */
	@Inject(method = "<init>", at = @At("TAIL"))
	private void honeycrisp$init(CallbackInfo ci) {
		try {
			Honeycrisp.init(McPlatform.INSTANCE);
			MenuActions.set(new McMenuActions());
		} catch (Throwable t) {
			LoggerFactory.getLogger("Honeycrisp Client").error("Honeycrisp Client failed to initialize; running vanilla", t);
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void honeycrisp$tick(CallbackInfo ci) {
		Hooks h = Honeycrisp.hooks();
		if (h == null) return;
		h.onClientTick();
		McRenderer.applyHitColor(h.hitColor());
		Options options = ((Minecraft) (Object) this).options;
		if (h.freelookThirdPerson()) {
			if (honeycrisp$savedCamera == null) {
				honeycrisp$savedCamera = options.getCameraType();
				if (honeycrisp$savedCamera.isFirstPerson()) options.setCameraType(CameraType.THIRD_PERSON_BACK);
			}
		} else if (honeycrisp$savedCamera != null) {
			options.setCameraType(honeycrisp$savedCamera);
			honeycrisp$savedCamera = null;
		}
	}
}
