package org.oryxel.viabedrockutility.mixin.impl.network;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerMixin {
    @Inject(method = "handleResourcePackPush", at = @At("HEAD"))
    private void viabedrockutility$logResourcePackPush(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        ViaBedrockUtilityNeoForge.LOGGER.info(
                "[ResourcePack] Received server resource pack push: id={}, required={}, url={}, hash={}, prompt={}",
                packet.id(),
                packet.required(),
                packet.url(),
                packet.hash(),
                packet.prompt().orElse(null)
        );
    }

    @Inject(method = "handleResourcePackPop", at = @At("HEAD"))
    private void viabedrockutility$logResourcePackPop(ClientboundResourcePackPopPacket packet, CallbackInfo ci) {
        ViaBedrockUtilityNeoForge.LOGGER.info("[ResourcePack] Received server resource pack pop: id={}", packet.id().orElse(null));
    }
}
