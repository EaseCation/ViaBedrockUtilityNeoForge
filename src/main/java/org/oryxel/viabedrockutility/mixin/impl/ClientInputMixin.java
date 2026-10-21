package org.oryxel.viabedrockutility.mixin.impl;

import net.minecraft.client.player.ClientInput;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.payload.PayloadHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientInput.class)
public class ClientInputMixin {
    @Inject(method = "makeJump", at = @At("HEAD"), cancellable = true, require = 1)
    private void vbu$lockAutoJump(CallbackInfo ci) {
        final PayloadHandler handler = ViaBedrockUtility.getInstance().getPayloadHandler();
        if (handler != null && handler.isJumpInputLocked()) {
            ci.cancel();
        }
    }
}
