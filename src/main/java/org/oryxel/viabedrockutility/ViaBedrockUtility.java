package org.oryxel.viabedrockutility;

import com.mojang.brigadier.Command;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.easecation.bedrockmotion.pack.PackManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.geom.ModelPart;
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
import org.oryxel.viabedrockutility.renderer.BedrockPlayerModelMetadata;
import org.oryxel.viabedrockutility.renderer.CustomPlayerRenderer;
import org.oryxel.viabedrockutility.animation.PlayerAnimationRuntime;
import org.oryxel.viabedrockutility.animation.PlayerAnimationState;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;
import net.easecation.bedrockmotion.controller.AnimationControllerInstance;
import org.oryxel.viabedrockutility.renderer.FrozenEntityMeshCache;
import org.oryxel.viabedrockutility.sound.BedrockJavaSoundPlayer;

import java.util.Map;
import java.util.List;
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
        final var attachableDebug = net.minecraft.commands.Commands.literal("vbuattachabledebug")
                .executes(context -> sendAttachableDebug(context, false))
                .then(net.minecraft.commands.Commands.literal("copy")
                        .executes(context -> sendAttachableDebug(context, true)))
                .then(net.minecraft.commands.Commands.literal("clear")
                        .executes(context -> {
                            this.attachableRuntimeManager.clear();
                            context.getSource().sendSuccess(() -> Component.literal("VBU attachable diagnostics cleared"), false);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(net.minecraft.commands.Commands.literal("mode")
                        .then(net.minecraft.commands.Commands.literal("auto")
                                .executes(context -> setAttachableDebugMode(context,
                                        AttachableRuntimeManager.DebugRenderMode.AUTO)))
                        .then(net.minecraft.commands.Commands.literal("java")
                                .executes(context -> setAttachableDebugMode(context,
                                        AttachableRuntimeManager.DebugRenderMode.JAVA_ITEM)))
                        .then(net.minecraft.commands.Commands.literal("vbu")
                                .executes(context -> setAttachableDebugMode(context,
                                        AttachableRuntimeManager.DebugRenderMode.VBU))));
        event.getDispatcher().register(attachableDebug);

        final var playerDebug = net.minecraft.commands.Commands.literal("vbuplayerdebug")
                .executes(context -> sendPlayerAnimationDebug(context, false))
                .then(net.minecraft.commands.Commands.literal("copy")
                        .executes(context -> sendPlayerAnimationDebug(context, true)));
        event.getDispatcher().register(playerDebug);

    }

    private int sendAttachableDebug(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context,
                                    boolean copyAll) {
        final var snapshots = this.attachableRuntimeManager.debugSnapshot();
        final var attempts = this.attachableRuntimeManager.debugAttempts();
        final LocalPlayer player = Minecraft.getInstance().player;
        final String header = "VBU attachables: generation=" + this.packGeneration.generation()
                + ", runtimes=" + snapshots.size() + ", attempts=" + attempts.size()
                + ", mode=" + this.attachableRuntimeManager.debugRenderMode()
                + (player == null ? "" : ", main=" + player.getMainHandItem().getItem()
                + ", off=" + player.getOffhandItem().getItem());
        final java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add(header);
        for (AttachableDebugLog.DebugAttempt attempt : attempts) {
            lines.add("attempt=" + attempt);
        }
        for (AttachableDebugLog.DebugInfo snapshot : snapshots) {
            lines.add(snapshot.runtimeKey() + " -> " + snapshot.identity()
                    + ", lastSeenTick=" + snapshot.lastSeenTick()
                    + ", binding=" + snapshot.bindingBone()
                    + ", hostProfile=" + snapshot.hostProfile()
                    + ", renderPath=" + snapshot.renderPath()
                    + ", semanticChain=" + snapshot.semanticChain()
                    + ", presentationChain=" + snapshot.presentationChain()
                    + ", controllers=" + snapshot.controllerStates()
                    + ", passes=" + snapshot.renderPasses()
                    + ", geometry=" + snapshot.geometrySummary()
                    + ", physicalAnchor=" + Arrays.toString(snapshot.physicalAnchorMatrix())
                    + ", geometryInstallation="
                    + Arrays.toString(snapshot.geometryInstallationMatrix()));
        }

        final String report = String.join("\n", lines);
        if (copyAll) {
            context.getSource().sendSuccess(() -> Component.literal("[复制完整 VBU attachable 诊断]")
                    .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.CopyToClipboard(report))), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal(header)
                    .append(Component.literal(" "))
                    .append(copyButton("[复制全部]", report)), false);
            for (String line : lines.subList(1, lines.size())) {
                context.getSource().sendSuccess(() -> Component.literal(line)
                        .append(Component.literal(" "))
                        .append(copyButton("[复制]", line)), false);
            }
        }
        for (String line : lines) {
            ViaBedrockUtilityNeoForge.LOGGER.info("[AttachableDebug] {}", line);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int setAttachableDebugMode(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context,
            AttachableRuntimeManager.DebugRenderMode mode) {
        this.attachableRuntimeManager.setDebugRenderMode(mode);
        context.getSource().sendSuccess(() -> Component.literal(
                "VBU attachable render mode=" + this.attachableRuntimeManager.debugRenderMode()), false);
        return Command.SINGLE_SUCCESS;
    }

    private int sendPlayerAnimationDebug(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context,
            boolean copyAll) {
        final LocalPlayer player = Minecraft.getInstance().player;
        final java.util.List<String> lines = new java.util.ArrayList<>();
        if (player == null || this.payloadHandler == null) {
            lines.add("VBU player animation: player or payload handler unavailable");
        } else {
            final Object renderer = this.payloadHandler.getCachedPlayerRenderers().get(player.getUUID());
            if (!(renderer instanceof CustomPlayerRenderer customRenderer)) {
                lines.add("VBU player animation: custom player renderer unavailable for " + player.getUUID());
            } else {
                lines.add("VBU player animation: uuid=" + player.getUUID()
                        + ", camera=" + Minecraft.getInstance().options.getCameraType()
                        + ", main=" + player.getMainHandItem().getItem()
                        + ", off=" + player.getOffhandItem().getItem());
                final PlayerAnimationRuntime.DebugSnapshot snapshot =
                        customRenderer.playerAnimationDebugSnapshot();
                if (snapshot == null) {
                    lines.add("runtime=unavailable");
                } else {
                    appendPlayerViewDebug(lines, "first_person", snapshot.firstPerson());
                    appendPlayerViewDebug(lines, "third_person", snapshot.thirdPerson());
                }
                final var renderTrace = org.oryxel.viabedrockutility.attachable.FirstPersonRenderTrace
                        .snapshot(player.getUUID());
                if (renderTrace == null) {
                    lines.add("first_person: renderTrace=unavailable");
                } else {
                    lines.add("first_person: renderTrace{frame=" + renderTrace.frameToken()
                            + ",stage=" + renderTrace.stage()
                            + ",arm=" + renderTrace.arm()
                            + ",pose=" + java.util.Arrays.toString(renderTrace.pose())
                            + ",stages=" + renderTrace.stages().entrySet().stream()
                            .map(entry -> entry.getKey() + ":" + java.util.Arrays.toString(entry.getValue().pose()))
                            .collect(java.util.stream.Collectors.joining(";")) + "}");
                }
                appendPlayerBones(lines, customRenderer.getPlayerModel());
            }
        }

        final String report = String.join("\n", lines);
        if (copyAll) {
            context.getSource().sendSuccess(() -> Component.literal("[复制完整 VBU player animation 诊断]")
                    .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.CopyToClipboard(report))), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("VBU player animation ")
                    .append(copyButton("[复制全部]", report)), false);
            for (String line : lines) {
                context.getSource().sendSuccess(() -> Component.literal(line)
                        .append(Component.literal(" "))
                        .append(copyButton("[复制]", line)), false);
            }
        }
        for (String line : lines) {
            ViaBedrockUtilityNeoForge.LOGGER.info("[PlayerAnimationDebug] {}", line);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void appendPlayerViewDebug(List<String> lines, String name,
                                               PlayerAnimationRuntime.ViewDebugSnapshot view) {
        final PlayerAnimationState state = view.state();
        if (state == null) {
            lines.add(name + ": state=unavailable");
            return;
        }
        lines.add(name + ": state{tick=" + state.tick()
                + ",partial=" + state.partialTick()
                + ",pitch=" + state.pitch()
                + ",relativeHeadYaw=" + state.relativeHeadYaw()
                + ",targetPitch=" + state.targetXRotation()
                + ",targetYaw=" + state.targetYRotation()
                + ",bodyYaw=" + state.bodyYaw()
                + ",walkPos=" + state.walkPosition()
                + ",walkSpeed=" + state.walkSpeed()
                + ",attack=" + state.attackTime()
                + ",swim=" + state.swimAmount()
                + ",main=" + state.mainHandIdentifier()
                + ",off=" + state.offHandIdentifier()
                + ",using=" + state.usingItem()
                + ",blocking=" + state.blocking()
                + ",onGround=" + state.onGround()
                + ",crouching=" + state.crouching()
                + ",swimming=" + state.swimming() + "}");
        final var runtime = view.runtime();
        lines.add(name + ": runtime{lastTick=" + runtime.lastTick()
                + ",partial=" + runtime.partialTick()
                + ",preparedFrame=" + runtime.preparedFrameTick()
                + ",preparedPartialBits=" + runtime.preparedPartialTickBits()
                + ",rootScale=" + runtime.rootScale() + "}");
        lines.add(name + ": playbacks=" + runtime.playbacks());
        lines.add(name + ": molang.query=" + view.queries());
        lines.add(name + ": molang.variable=" + view.variables());
        for (AnimationControllerInstance.ControllerDebugSnapshot controller : runtime.controllers()) {
            appendControllerDebug(lines, name + ": controller", controller, "");
        }
    }

    private static void appendControllerDebug(List<String> lines, String prefix,
                                              AnimationControllerInstance.ControllerDebugSnapshot controller,
                                              String indent) {
        lines.add(prefix + indent + "{" + controller.identifier()
                + ",state=" + controller.stateName()
                + ",blend=" + controller.blendWeight()
                + ",incoming=" + controller.incomingFactor()
                + ",stateTime=" + controller.stateTimeSeconds()
                + ",fading=" + controller.fadingStateCount()
                + ",entries=" + controller.entries().stream()
                .map(entry -> entry.identifier() + "@" + entry.baseWeight()
                        + (entry.done() ? ":done" : "")
                        + (entry.childController() == null ? "" : ":child"))
                .toList() + "}");
        for (AnimationControllerInstance.PlaybackDebugSnapshot entry : controller.entries()) {
            if (entry.childController() != null) {
                appendControllerDebug(lines, prefix, entry.childController(), indent + "/");
            }
        }
    }

    private static void appendPlayerBones(List<String> lines, net.minecraft.client.model.PlayerModel model) {
        // Enumerate the complete registered model rather than baking the current player bone list
        // into the diagnostic command. Future Bedrock bones then appear automatically.
        final java.util.Set<ModelPart> emitted = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>());
        int unnamed = 0;
        for (ModelPart part : model.allParts()) {
            String name = ((IModelPart) (Object) part).viaBedrockUtility$getName();
            if (name == null || name.isBlank()) {
                name = "unnamed_" + unnamed++;
            }
            appendModelPart(lines, name, part);
            emitted.add(part);
        }
        final BedrockPlayerModelMetadata metadata = BedrockPlayerModelMetadata.get(model);
        if (metadata == null) {
            lines.add("bones=metadata unavailable");
            return;
        }
        for (BedrockPlayerModelMetadata.Bone bone : metadata.bones()) {
            final ModelPart part = bone.part();
            if (part != null && emitted.add(part)) {
                appendModelPart(lines, bone.key(), part);
            } else if (part == null) {
                lines.add("bone=" + bone.key() + "{virtual=true,semanticParent="
                        + bone.parentKey() + ",presentationParent="
                        + bone.presentationParentKey() + ",pivot=" + bone.pivot()
                        + ",rotation=" + bone.rotation() + "}");
            }
        }
    }

    private static void appendModelPart(List<String> lines, String key, ModelPart part) {
        final IModelPart extension = (IModelPart) (Object) part;
        lines.add("bone=" + key + "{x=" + part.x + ",y=" + part.y + ",z=" + part.z
                + ",xRot=" + part.xRot + ",yRot=" + part.yRot + ",zRot=" + part.zRot
                + ",scale=" + part.xScale + "/" + part.yScale + "/" + part.zScale
                + ",bedrockRotation=" + extension.viaBedrockUtility$getRotation()
                + ",bedrockOffset=" + extension.viaBedrockUtility$getOffset()
                + ",bedrockPivot=" + extension.viaBedrockUtility$getPivot()
                + ",rotationUnit=degrees,offsetUnit=pixels}");
    }

    private static Component copyButton(String label, String value) {
        return Component.literal(label).withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(value)));
    }
}
