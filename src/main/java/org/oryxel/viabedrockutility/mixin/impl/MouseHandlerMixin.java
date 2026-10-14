package org.oryxel.viabedrockutility.mixin.impl;

import net.minecraft.client.MouseHandler;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.payload.PayloadHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true, require = 1)
    private void vbu$lockCamera(double elapsedTime, CallbackInfo ci) {
        final PayloadHandler handler = ViaBedrockUtility.getInstance().getPayloadHandler();
        if (handler != null && handler.isCameraInputLocked()) {
            ci.cancel();
        }
    }
}
