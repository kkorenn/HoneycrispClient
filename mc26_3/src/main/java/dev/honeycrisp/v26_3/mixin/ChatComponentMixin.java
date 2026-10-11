package dev.honeycrisp.v26_3.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Hooks;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Double.floatValue ordinal 0 is chatOpacity, 1 is textBackgroundOpacity. */
@Mixin(ChatComponent.class)
public class ChatComponentMixin {
	@ModifyExpressionValue(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
		at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 1))
	private float honeycrisp$background(float opacity) {
		Hooks h = Honeycrisp.hooks();
		float o = h == null ? -1 : h.chatBackgroundOpacity();
		return o < 0 ? opacity : o;
	}
}
