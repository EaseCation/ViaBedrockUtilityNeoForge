package org.oryxel.viabedrockutility.mixin.impl.network;

import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import org.oryxel.viabedrockutility.payload.BasePayload;
import org.oryxel.viabedrockutility.payload.impl.camera.CameraPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("UnstableApiUsage")
@Mixin(ClientConfigurationPacketListenerImpl.class)
public class ClientConfigurationNetworkHandlerMixin {
    @Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V", at = @At("HEAD"), cancellable = true)
    private void handleCustomPayload(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        ClientConfigurationPacketListener listener = (ClientConfigurationPacketListener) (Object) this;
        if (ChannelAttributes.getPayloadSetup(listener.getConnection()) != null) {
            return;
        }

        if (packet.payload().type().equals(BasePayload.ID)
                && packet.payload().getClass() == BasePayload.class
                && ViaBedrockUtility.getInstance().isViaBedrockPresent()) {
            ViaBedrockUtilityNeoForge.LOGGER.debug("[Handshake] Handled early ViaBedrock CONFIRM before NeoForge payload setup");
            ci.cancel();
            return;
        }

        if (packet.payload() instanceof CameraPayload cameraPayload
                && cameraPayload.getType() == CameraPayload.CameraPayloadType.CONFIRM) {
            ViaBedrockUtilityNeoForge.LOGGER.debug("[BECamera] Handled early CONFIRM before NeoForge payload setup");
            ci.cancel();
        }
    }
}
