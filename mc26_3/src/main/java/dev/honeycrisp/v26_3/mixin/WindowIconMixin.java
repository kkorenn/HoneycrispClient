package dev.honeycrisp.v26_3.mixin;

import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.server.packs.PackMetadataResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Started by the Honeycrisp launcher (-Dhoneycrisp.appIcon=true): keep the Honeycrisp Client Dock icon instead of the grass block. 26.3 sets it through SDL here (MacosUtil.loadIcon is gone). */
@Mixin(Window.class)
public class WindowIconMixin {
	@Inject(method = "setIcon(Lnet/minecraft/server/packs/PackMetadataResources;Lcom/mojang/blaze3d/platform/IconSet;)V", at = @At("HEAD"), cancellable = true)
	private void honeycrisp$keepAppIcon(PackMetadataResources resources, IconSet icons, CallbackInfo ci) {
		if (Boolean.getBoolean("honeycrisp.appIcon")) ci.cancel();
	}
}
