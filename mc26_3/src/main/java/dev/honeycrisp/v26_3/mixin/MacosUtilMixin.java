package dev.honeycrisp.v26_3.mixin;

import com.mojang.blaze3d.platform.MacosUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Started by the Honeycrisp launcher (-Dhoneycrisp.appIcon=true): keep the Honeycrisp Client Dock icon instead of the grass block. */
@Mixin(MacosUtil.class)
public class MacosUtilMixin {
	@Inject(method = "loadIcon", at = @At("HEAD"), cancellable = true)
	private static void honeycrisp$keepAppIcon(CallbackInfo ci) {
		if (Boolean.getBoolean("honeycrisp.appIcon")) ci.cancel();
	}
}
