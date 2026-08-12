package org.oryxel.viabedrockutility;

import com.mojang.brigadier.Command;
import lombok.Getter;
import lombok.Setter;
import net.easecation.bedrockmotion.pack.PackManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.oryxel.viabedrockutility.mappings.BedrockMappings;
import org.oryxel.viabedrockutility.material.VanillaMaterials;
import org.oryxel.viabedrockutility.network.PlayerStateFlags;
import org.oryxel.viabedrockutility.network.PlayerStateTracker;
import org.oryxel.viabedrockutility.payload.BasePayload;
import org.oryxel.viabedrockutility.payload.PlayerStatePayload;
import org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler;
import org.oryxel.viabedrockutility.payload.impl.camera.CameraPayload;
import org.oryxel.viabedrockutility.payload.impl.camera.CameraPayloadHandler;

@Getter
@Setter
public class ViaBedrockUtility {
    public static boolean DEBUGGING = true;

    @Getter
    private static final ViaBedrockUtility instance = new ViaBedrockUtility();

    private ViaBedrockUtility() {}

    private CustomEntityPayloadHandler payloadHandler;
    private CameraPayloadHandler cameraPayloadHandler;
    private PackManager packManager;
    private volatile boolean viaBedrockPresent;
    private final PlayerStateTracker playerStateTracker = new PlayerStateTracker();

    public void init() {
        VanillaMaterials.init();
        BedrockMappings.load();

        this.payloadHandler = new CustomEntityPayloadHandler();
        this.cameraPayloadHandler = new CameraPayloadHandler();
    }

    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        // Register custom payload.
        registrar.configurationToClient(BasePayload.ID, BasePayload.STREAM_CODEC, (payload, context) -> payload.handle(this.payloadHandler));
        registrar.playToClient(BasePayload.ID, BasePayload.STREAM_CODEC, (payload, context) -> payload.handle(this.payloadHandler));
        // Register BECamera payload channel (CONFIGURATION for CONFIRM, PLAY for data).
        registrar.configurationToClient(CameraPayload.ID, CameraPayload.STREAM_CODEC, (payload, context) -> payload.handle(this.cameraPayloadHandler));
        registrar.playToClient(CameraPayload.ID, CameraPayload.STREAM_CODEC, (payload, context) -> payload.handle(this.cameraPayloadHandler));
        registrar.playToServer(PlayerStatePayload.TYPE, PlayerStatePayload.STREAM_CODEC, (payload, context) -> {
        });
    }

    public void onClientTick(ClientTickEvent.Post event) {
        // Tick animation overlays on all cached player renderers
        if (this.payloadHandler != null) {
            this.payloadHandler.tickAnimationOverlays();
        }

        final LocalPlayer player = Minecraft.getInstance().player;
        final int stateFlags = player == null ? 0 : PlayerStateFlags.create(
                PlayerStateFlags.isCrawling(
                        player.isVisuallySwimming(),
                        player.isInWater(),
                        player.isInFluidType((fluidType, height) -> player.canSwimInFluidType(fluidType))
                ),
                player.isShiftKeyDown(),
                player.isSprinting(),
                player.isSwimming(),
                player.isFallFlying(),
                player.getAbilities().flying
        );
        final Integer stateToSend = this.playerStateTracker.stateToSend(player, this.viaBedrockPresent, stateFlags);
        if (stateToSend != null) {
            ClientPacketDistributor.sendToServer(new PlayerStatePayload(stateToSend));
            this.playerStateTracker.markSent(player, stateToSend);
        }
    }

    public void resetPlayerState() {
        this.playerStateTracker.reset();
    }

    public void registerClientCommands(RegisterClientCommandsEvent event) {
        // To enable debugging in order to use animate test thingy (look at ClientPlayNetworkHandler)
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("vbudebug").executes(context -> {
            DEBUGGING = !DEBUGGING;
            System.out.println("Debugging status: " + DEBUGGING);
            return Command.SINGLE_SUCCESS;
        }));
    }
}
