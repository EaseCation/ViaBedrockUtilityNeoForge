package org.oryxel.viabedrockutility.mixin.impl.network;

import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.payload.MinecraftRegisterPayload;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import org.oryxel.viabedrockutility.payload.impl.camera.CameraPayload;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@SuppressWarnings("UnstableApiUsage")
@Mixin(ClientHandshakePacketListenerImpl.class)
public class ClientLoginNetworkHandlerMixin {
    @Shadow
    @Final
    private Connection connection;

    @Inject(method = "handleLoginFinished", at = @At("RETURN"))
    public void handleLoginFinished(ClientboundLoginFinishedPacket packet, CallbackInfo ci) {
        // Let ViaBedrock know that we want to receive full bedrock pack, also we have to do this to send it early.
        // Also use a different ResourceLocation to avoiding sending the same one twice.
        ViaBedrockUtility.getInstance().setViaBedrockPresent(false);
        ViaBedrockUtilityNeoForge.LOGGER.info("[Handshake] Login success, sending confirm channel registration to ViaBedrock...");
        this.connection.send(new ServerboundCustomPayloadPacket(new MinecraftRegisterPayload(Set.of(
                ResourceLocation.fromNamespaceAndPath(ViaBedrockUtilityNeoForge.MOD_ID, "confirm"),
                ResourceLocation.fromNamespaceAndPath(CameraPayload.CONFIRM_CHANNEL_ID, CameraPayload.CONFIRM_CHANNEL_PATH)
        ))));
    }
}
