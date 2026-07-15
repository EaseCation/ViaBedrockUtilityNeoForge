package org.oryxel.viabedrockutility.mixin.impl.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import org.oryxel.viabedrockutility.network.ServerResourcePackPolicy;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerMixin {
    @Shadow
    @Final
    protected Minecraft minecraft;

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

    @ModifyVariable(
            method = "handleResourcePackPush",
            at = @At(value = "STORE"),
            ordinal = 0
    )
    private ServerData.ServerPackStatus viabedrockutility$autoAcceptServerResourcePacks(
            ServerData.ServerPackStatus currentStatus
    ) {
        ViaBedrockUtilityNeoForge.LOGGER.info(
                "[ResourcePack] Auto-accepting server resource pack (savedStatus={})",
                currentStatus
        );
        return ServerResourcePackPolicy.autoAccept(
                currentStatus,
                this.minecraft.getDownloadedPackSource()::allowServerPacks
        );
    }

    @Inject(method = "handleResourcePackPop", at = @At("HEAD"))
    private void viabedrockutility$logResourcePackPop(ClientboundResourcePackPopPacket packet, CallbackInfo ci) {
        ViaBedrockUtilityNeoForge.LOGGER.info("[ResourcePack] Received server resource pack pop: id={}", packet.id().orElse(null));
    }
}
