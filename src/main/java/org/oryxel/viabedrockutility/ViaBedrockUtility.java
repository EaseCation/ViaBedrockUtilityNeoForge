package org.oryxel.viabedrockutility;

import com.mojang.brigadier.Command;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.easecation.bedrockmotion.pack.PackManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
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
import org.oryxel.viabedrockutility.attachable.AttachableDebugLog;
import org.oryxel.viabedrockutility.attachable.AttachableRuntimeManager;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import org.oryxel.viabedrockutility.particle.BedrockParticleRuntime;
import org.oryxel.viabedrockutility.particle.BedrockParticleRequest;
import org.oryxel.viabedrockutility.pack.processor.TextureProcessor;
import org.oryxel.viabedrockutility.renderer.BedrockPlayerPoseDemand;
import org.oryxel.viabedrockutility.renderer.FrozenEntityMeshCache;
import org.oryxel.viabedrockutility.sound.BedrockJavaSoundPlayer;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Arrays;

@Getter
@Setter
public class ViaBedrockUtility {
    public static boolean DEBUGGING = true;

    @Getter
    private static final ViaBedrockUtility instance = new ViaBedrockUtility();

    private ViaBedrockUtility() {}

    // Class-level @Setter must not expose these: the handlers are init-owned, and packGeneration
    // may only be published through setPackManager (atomic generation bump + runtime invalidation).
    @Setter(AccessLevel.NONE)
    private CustomEntityPayloadHandler payloadHandler;
    @Setter(AccessLevel.NONE)
    private CameraPayloadHandler cameraPayloadHandler;
    @Setter(AccessLevel.NONE)
    private volatile PackGeneration packGeneration = new PackGeneration(0L, null);
    private final AtomicLong packGenerationCounter = new AtomicLong();
    private final AtomicLong connectionEpochCounter = new AtomicLong();
    private volatile long connectionEpoch;
    private final AttachableRuntimeManager attachableRuntimeManager = new AttachableRuntimeManager();
    private final BedrockPlayerPoseDemand playerPoseDemand = new BedrockPlayerPoseDemand();
    private final BedrockParticleRuntime particleRuntime = new BedrockParticleRuntime(this.playerPoseDemand);
    private volatile boolean viaBedrockPresent;
    private final PlayerStateTracker playerStateTracker = new PlayerStateTracker();

    public void init() {
        VanillaMaterials.init();
        BedrockMappings.load();

        // ViaBedrock owns Bedrock sound conversion and registers bedrock:<effect> Java events.
        // BEParticle only reports an effect identifier; VBU forwards it to Minecraft's native
        // sound manager without maintaining a second definition or audio-resource runtime.
        net.easecation.beparticle.ParticleManager.soundSink = (effect, position, locator, variables) -> {
            BedrockJavaSoundPlayer.playAt(effect, position.x, position.y, position.z,
                    net.minecraft.sounds.SoundSource.MASTER, 1.0F, 1.0F);
        };

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
        this.attachableRuntimeManager.tick();
        this.particleRuntime.tick();
        final var level = Minecraft.getInstance().level;
        this.playerPoseDemand.prune(level == null ? Long.MIN_VALUE : level.getGameTime());
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

    public PackManager getPackManager() {
        return this.packGeneration.manager();
    }

    /** Publishes manager+generation atomically, then invalidates every mutable attachable instance. */
    public synchronized void setPackManager(PackManager manager) {
        final long generation = this.packGenerationCounter.incrementAndGet();
        this.packGeneration = new PackGeneration(generation, manager);
        if (this.payloadHandler != null) {
            this.payloadHandler.onPackManagerChanged(manager);
        }
        this.attachableRuntimeManager.onPackManagerChanged(manager);
        this.attachableRuntimeManager.clear();
        this.playerPoseDemand.clear();
        this.particleRuntime.clearDiagnostics();
        BedrockJavaSoundPlayer.clearDiagnostics();
        FrozenEntityMeshCache.global().invalidateAll("pack_generation_changed");
        net.easecation.beparticle.ParticleManager.INSTANCE
                .clearEmitters(BedrockParticleRuntime.LIFECYCLE_OWNER);
        net.easecation.beparticle.molang.ParticleMoLang.INSTANCE.clearCache();
        net.easecation.beparticle.render.BedrockParticleManager.INSTANCE.clear();
    }

    /** Publishes only if the resource reload still belongs to the active connection. */
    public synchronized boolean publishPackManager(PackManager manager, long expectedConnectionEpoch) {
        if (expectedConnectionEpoch != this.connectionEpoch) {
            ViaBedrockUtilityNeoForge.LOGGER.debug(
                    "[ResourcePack] Discarding retired connection generation {} (current={})",
                    expectedConnectionEpoch, this.connectionEpoch);
            return false;
        }
        this.setPackManager(manager);
        return true;
    }

    public boolean isCurrentConnectionEpoch(long expectedConnectionEpoch) {
        return expectedConnectionEpoch == this.connectionEpoch;
    }

    /** Starts a clean connection scope before any new resource-pack payload can be accepted. */
    public long beginConnection() {
        return resetConnectionResources();
    }

    /** Retires the current scope so late resource work cannot publish into a later connection. */
    public void endConnection() {
        resetConnectionResources();
    }

    private synchronized long resetConnectionResources() {
        final long nextEpoch = this.connectionEpochCounter.incrementAndGet();
        this.connectionEpoch = nextEpoch;
        this.viaBedrockPresent = false;
        this.playerStateTracker.reset();
        this.setPackManager(null);
        if (this.payloadHandler != null) {
            this.payloadHandler.resetConnectionState();
        }
        TextureProcessor.clear();
        net.easecation.beparticle.ParticleManager.INSTANCE
                .loadDefinitions("viabedrockutility", Map.of());
        return nextEpoch;
    }

    /**
     * Spawns a resource-pack Bedrock particle through VBU's BEParticle runtime.
     *
     * <p>This is the cross-mod boundary for client integrations. Callers must not query Java
     * {@code ParticleType} registries or construct vanilla particles for a Bedrock definition.</p>
     */
    public boolean spawnParticle(String identifier, float x, float y, float z,
                                 Map<String, Float> molangVariables) {
        return this.particleRuntime.spawn(identifier, x, y, z, molangVariables);
    }

    public boolean spawnParticle(final BedrockParticleRequest request) {
        return this.particleRuntime.spawn(request);
    }

    public net.easecation.beparticle.ParticleSpawnResult spawnParticleResult(final BedrockParticleRequest request) {
        return this.particleRuntime.spawnResult(request);
    }

    public void playParticleSound(String identifier, float x, float y, float z,
                                  String locator, Map<String, Float> variables) {
        final var sink = net.easecation.beparticle.ParticleManager.soundSink;
        if (sink != null) {
            sink.play(identifier, new org.joml.Vector3f(x, y, z), locator, variables);
        }
    }

    /** Convenience overload for effects without external MoLang variables. */
    public boolean spawnParticle(String identifier, float x, float y, float z) {
        return this.spawnParticle(identifier, x, y, z, null);
    }

    public record PackGeneration(long generation, PackManager manager) {
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
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("vbuattachabledebug")
                .executes(context -> {
                    final var snapshots = this.attachableRuntimeManager.debugSnapshot();
                    final var attempts = this.attachableRuntimeManager.debugAttempts();
                    final LocalPlayer player = Minecraft.getInstance().player;
                    final String header = "VBU attachables: generation=" + this.packGeneration.generation()
                            + ", runtimes=" + snapshots.size() + ", attempts=" + attempts.size()
                            + (player == null ? "" : ", main=" + player.getMainHandItem().getItem()
                            + ", off=" + player.getOffhandItem().getItem());
                    context.getSource().sendSuccess(() -> Component.literal(header), false);
                    ViaBedrockUtilityNeoForge.LOGGER.info(header);
                    for (AttachableDebugLog.DebugAttempt attempt : attempts) {
                        final String line = "attempt=" + attempt;
                        context.getSource().sendSuccess(() -> Component.literal(line), false);
                        ViaBedrockUtilityNeoForge.LOGGER.info("[AttachableDebug] {}", line);
                    }
                    for (AttachableDebugLog.DebugInfo snapshot : snapshots) {
                        final String line = snapshot.runtimeKey() + " -> " + snapshot.identity()
                                + ", binding=" + snapshot.bindingBone()
                                + ", hostProfile=" + snapshot.hostProfile()
                                + ", semanticChain=" + snapshot.semanticChain()
                                + ", presentationChain=" + snapshot.presentationChain()
                                + ", controllers=" + snapshot.controllerStates()
                                + ", passes=" + snapshot.renderPasses()
                                + ", host=" + Arrays.toString(snapshot.hostMatrix());
                        context.getSource().sendSuccess(() -> Component.literal(line), false);
                        ViaBedrockUtilityNeoForge.LOGGER.info("[AttachableDebug] {}", line);
                    }
                    return Command.SINGLE_SUCCESS;
                }));
    }
}
