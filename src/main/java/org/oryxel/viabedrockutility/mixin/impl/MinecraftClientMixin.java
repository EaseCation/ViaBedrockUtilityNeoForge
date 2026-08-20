package org.oryxel.viabedrockutility.mixin.impl;

import nakern.be_camera.camera.CameraManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.renderer.FrozenEntityMeshCache;
import org.oryxel.viabedrockutility.renderer.FrozenMeshDrawQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {
    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("HEAD"))
    private void disconnect(Screen disconnectionScreen, boolean transferring, CallbackInfo ci) {
        FrozenMeshDrawQueue.clear();
        FrozenEntityMeshCache.global().invalidateAll("disconnect");
        ViaBedrockUtility.getInstance().setViaBedrockPresent(false);
        ViaBedrockUtility.getInstance().resetPlayerState();
        ViaBedrockUtility.getInstance().getAttachableRuntimeManager().clear();
        if (ViaBedrockUtility.getInstance().getPayloadHandler() == null) {
            return;
        }

        ViaBedrockUtility.getInstance().getPayloadHandler().getCachedPlayerRenderers().clear();
        ViaBedrockUtility.getInstance().getPayloadHandler().getCachedCustomEntities().clear();
        ViaBedrockUtility.getInstance().getPayloadHandler().getCachedPlayerCapes().clear();
        ViaBedrockUtility.getInstance().getPayloadHandler().getCachedPlayerSkins().clear();
        ViaBedrockUtility.getInstance().getPayloadHandler().getCachedSkinInfo().clear();
        ViaBedrockUtility.getInstance().getPayloadHandler().getPendingAnimations().clear();
        ViaBedrockUtility.getInstance().getPayloadHandler().getPendingPayloads().clear();

        // Reset BECamera state
        CameraManager.INSTANCE.resetAll();

        // Clear BEParticle emitters (keep definitions for reconnect)
        net.easecation.beparticle.ParticleManager.INSTANCE.clearEmitters();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void vbu$closeFrozenMeshes(CallbackInfo ci) {
        FrozenMeshDrawQueue.clear();
        FrozenEntityMeshCache.global().invalidateAll("client_close");
    }
}
