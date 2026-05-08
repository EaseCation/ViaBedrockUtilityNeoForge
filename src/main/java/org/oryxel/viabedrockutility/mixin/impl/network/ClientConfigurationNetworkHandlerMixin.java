package org.oryxel.viabedrockutility.mixin.impl.network;

import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import net.minecraft.network.protocol.configuration.ClientboundUpdateEnabledFeaturesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.neoforged.neoforge.network.payload.MinecraftRegisterPayload;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import org.oryxel.viabedrockutility.payload.impl.camera.CameraPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@SuppressWarnings("UnstableApiUsage")
@Mixin(ClientConfigurationPacketListenerImpl.class)
public class ClientConfigurationNetworkHandlerMixin {
    @Unique
    private boolean viaBedrockUtility$sentConfirmRegistration;

    @Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V", at = @At("RETURN"))
    private void handleCustomPayload(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        if (packet.payload() instanceof BrandPayload) {
            this.viaBedrockUtility$sendConfirmRegistration();
        }
    }

    @Inject(method = "handleEnabledFeatures", at = @At("RETURN"))
    private void handleEnabledFeatures(ClientboundUpdateEnabledFeaturesPacket packet, CallbackInfo ci) {
        this.viaBedrockUtility$sendConfirmRegistration();
    }

    @Unique
    private void viaBedrockUtility$sendConfirmRegistration() {
        if (this.viaBedrockUtility$sentConfirmRegistration) {
            return;
        }

        ClientConfigurationPacketListener listener = (ClientConfigurationPacketListener) (Object) this;
        if (ChannelAttributes.getPayloadSetup(listener.getConnection()) == null) {
            return;
        }

        this.viaBedrockUtility$sentConfirmRegistration = true;
        ViaBedrockUtility.getInstance().setViaBedrockPresent(false);
        ViaBedrockUtilityNeoForge.LOGGER.info("[Handshake] Configuration channel ready, sending confirm channel registration to ViaBedrock...");
        listener.send(new MinecraftRegisterPayload(Set.of(
                ResourceLocation.fromNamespaceAndPath(ViaBedrockUtilityNeoForge.MOD_ID, "confirm"),
                ResourceLocation.fromNamespaceAndPath(CameraPayload.CONFIRM_CHANNEL_ID, CameraPayload.CONFIRM_CHANNEL_PATH)
        )));
    }
}
