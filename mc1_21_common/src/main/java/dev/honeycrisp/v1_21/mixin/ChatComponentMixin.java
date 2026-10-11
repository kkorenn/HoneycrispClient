package dev.honeycrisp.v1_21.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.honeycrisp.Honeycrisp;
import dev.honeycrisp.hooks.Hooks;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/** Chat background opacity: the first OptionInstance.get() after options.textBackgroundOpacity(). */
@Mixin(ChatComponent.class)
public class ChatComponentMixin {
	@ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0),
		slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;textBackgroundOpacity()Lnet/minecraft/client/OptionInstance;")))
	private Object honeycrisp$opacity(Object opacity) {
		Hooks h = Honeycrisp.hooks();
		float o = h == null ? -1 : h.chatBackgroundOpacity();
		return o < 0 ? opacity : Double.valueOf(o);
	}
}
