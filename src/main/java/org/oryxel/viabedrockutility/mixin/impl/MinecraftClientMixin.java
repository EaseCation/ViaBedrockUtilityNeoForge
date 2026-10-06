package org.oryxel.viabedrockutility.mixin.impl;

import nakern.be_camera.camera.CameraManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.oryxel.viabedrockutility.payload.PayloadHandler;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.animation.BedrockFrameTime;
import org.oryxel.viabedrockutility.renderer.FrozenEntityMeshCache;
import org.oryxel.viabedrockutility.renderer.FrozenMeshDrawQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {
    @Inject(method = "setLevel", at = @At("HEAD"), require = 1)
    private void clearHurtOnLevelChange(ClientLevel level, ReceivingLevelScreen.Reason reason, CallbackInfo ci) {
        if (((Minecraft) (Object) this).level != level) {
            final PayloadHandler handler = ViaBedrockUtility.getInstance().getPayloadHandler();
            if (handler != null) {
                handler.getCustomEntityHurtTracker().clearAll();
            }
        }
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void vbu$beginMolangFrame(boolean renderLevel, CallbackInfo ci) {
        BedrockFrameTime.INSTANCE.beginFrame(System.nanoTime());
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("HEAD"))
    private void disconnect(Screen disconnectionScreen, boolean transferring, CallbackInfo ci) {
        FrozenMeshDrawQueue.clear();
        FrozenEntityMeshCache.global().invalidateAll("disconnect");
        ViaBedrockUtility.getInstance().endConnection();

        // Reset BECamera state
        CameraManager.INSTANCE.resetAll();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void vbu$closeFrozenMeshes(CallbackInfo ci) {
        FrozenMeshDrawQueue.clear();
        FrozenEntityMeshCache.global().invalidateAll("client_close");
    }
}
