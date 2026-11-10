package org.oryxel.viabedrockutility.payload;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.model.PlayerModel;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.entity.CustomEntityTicker;
import org.oryxel.viabedrockutility.entity.CustomEntityHurtTracker;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import org.oryxel.viabedrockutility.mixin.impl.accessor.PlayerSkinFieldAccessor;
import net.easecation.bedrockmotion.pack.PackManager;
import org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler;
import org.oryxel.viabedrockutility.payload.impl.entity.AnimatePayload;
import org.oryxel.viabedrockutility.payload.impl.entity.ModelRequestPayload;
import org.oryxel.viabedrockutility.payload.impl.entity.CustomEntityMotionPayload;
import org.oryxel.viabedrockutility.payload.impl.skin.BaseSkinPayload;
import org.oryxel.viabedrockutility.payload.impl.skin.CapeDataPayload;
import org.oryxel.viabedrockutility.payload.impl.skin.SkinAnimationDataPayload;
import org.oryxel.viabedrockutility.payload.impl.skin.SkinAnimationInfoPayload;
import org.oryxel.viabedrockutility.payload.impl.skin.SkinDataPayload;
import org.oryxel.viabedrockutility.payload.impl.particle.SpawnParticlePayload;
import org.oryxel.viabedrockutility.payload.impl.particle.SpawnParticleV2Payload;
import org.oryxel.viabedrockutility.payload.impl.player.PlayerVisualStatePayload;
import org.oryxel.viabedrockutility.payload.impl.player.PlayerAnimatePayload;
import org.oryxel.viabedrockutility.payload.impl.player.InputLocksPayload;
import org.oryxel.viabedrockutility.payload.impl.player.InputLocksV2Payload;
import org.oryxel.viabedrockutility.payload.impl.player.InputLocksV3Payload;
import org.oryxel.viabedrockutility.mixin.impl.accessor.ClientInputAccessor;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.easecation.bedrockmotion.pack.definitions.AnimationDefinitions;
import org.oryxel.viabedrockutility.renderer.AnimatedSkinOverlay;
import org.oryxel.viabedrockutility.renderer.CustomPlayerRenderer;
import org.oryxel.viabedrockutility.util.EntityRendererContextUtil;
import org.oryxel.viabedrockutility.util.BundledPlayerModelFactory;
import org.oryxel.viabedrockutility.util.GeometryUtil;

import org.oryxel.viabedrockutility.util.ImageUtil;
import org.oryxel.viabedrockutility.util.PlayerSkinBuilder;
import org.oryxel.viabedrockutility.util.PlayerSkinTextureCompat;
import org.oryxel.viabedrockutility.diagnostics.SkinDebugLog;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class PayloadHandler {
    private final SkinWorkQueue skinWork = new SkinWorkQueue(256, 16, 128L * 1024 * 1024);
    private final Map<UUID, Long> skinTransfers = new HashMap<>();
    private final Map<UUID, Long> installedSkinTransfers = new HashMap<>();
    private long rejectedSkinInputs;
    private int inputLockFlags;
    protected final Map<UUID, CustomEntityTicker> cachedCustomEntities = new ConcurrentHashMap<>();
    private final CustomEntityHurtTracker customEntityHurtTracker = new CustomEntityHurtTracker();
    protected final Map<UUID, EntityRenderer<?, ?>> cachedPlayerRenderers = new ConcurrentHashMap<>();
    protected final Map<UUID, ResourceLocation> cachedPlayerCapes = new ConcurrentHashMap<>();
    protected final Map<UUID, SkinInfo> cachedSkinInfo = new ConcurrentHashMap<>();
    protected final Map<UUID, CachedPlayerSkin> cachedPlayerSkins = new ConcurrentHashMap<>();
    protected final Map<UUID, Map<Integer, PendingAnimation>> pendingAnimations = new ConcurrentHashMap<>();
    protected final Map<UUID, Integer> playerVisualStates = new ConcurrentHashMap<>();
    protected final SkinDebugLog skinDebugLog = new SkinDebugLog();
    protected PackManager packManager;

    // Payloads that arrive before the resource pack (PackManager) is ready are queued here instead of being
    // dropped, then replayed in arrival order once the pack loads (see flushPendingPayloads). This fixes the
    // initial skin overrides the server pushes right at join, which used to be discarded with no retry.
    // The cap guards against unbounded growth on servers that never send a resource pack.
    private final java.util.Queue<BasePayload> pendingPayloads = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final int MAX_PENDING = 1024;
    private boolean replayingPending;

    /** Switches every pack-backed consumer to the newly published generation. */
    public void onPackManagerChanged(PackManager manager) {
        this.packManager = manager;
        resetPlayerAnimationRuntimes();
    }

    /** Rebuilds controller and one-shot state after an ordinary client resource reload. */
    public void resetPlayerAnimationRuntimes() {
        final PackManager manager = this.packManager;
        if (manager == null) {
            cachedPlayerRenderers.values().forEach(renderer -> {
                if (renderer instanceof CustomPlayerRenderer customRenderer) {
                    customRenderer.clearPlayerAnimationRuntime();
                }
            });
            return;
        }
        for (Map.Entry<UUID, EntityRenderer<?, ?>> entry : cachedPlayerRenderers.entrySet()) {
            if (entry.getValue() instanceof CustomPlayerRenderer renderer) {
                final CachedPlayerSkin skin = cachedPlayerSkins.get(entry.getKey());
                applyAnimationOverrides(entry.getKey(), renderer,
                        skin == null ? null : skin.getResourcePatch(), manager);
            }
        }
    }

    /** Clears all state whose identity belongs to the retired network connection. */
    public void resetConnectionState() {
        skinWork.clear();
        skinTransfers.clear();
        installedSkinTransfers.clear();
        this.customEntityMotionEnabled = false;
        this.witherSkullMotionEnabled = false;
        clearInputLocks();
        clearCustomEntityModels();
        cachedPlayerRenderers.clear();
        cachedPlayerCapes.clear();
        cachedPlayerSkins.clear();
        cachedSkinInfo.clear();
        pendingAnimations.clear();
        playerVisualStates.clear();
        pendingPayloads.clear();
        skinDebugLog.clear();
        packManager = null;
    }

    /** World-owned custom models must not survive a world replacement; player skin caches may. */
    public void clearCustomEntityModels() {
        customEntityHurtTracker.clearAll();
        cachedCustomEntities.values().forEach(ticker ->
                ticker.getRenderer().invalidateFrozenMeshes("world_reset"));
        cachedCustomEntities.clear();
    }

    private boolean immobile;

    public void clearInputLocks() {
        this.inputLockFlags = 0;
        this.immobile = false;
    }

    public boolean isMovementInputLocked() {
        return this.immobile || (this.inputLockFlags & InputLocksPayload.MOVEMENT) != 0;
    }

    public boolean isImmobile() {
        return this.immobile;
    }

    public boolean isCameraInputLocked() {
        return (this.inputLockFlags & InputLocksPayload.CAMERA) != 0;
    }

    public boolean isJumpInputLocked() {
        return this.isMovementInputLocked() || (this.inputLockFlags & InputLocksV3Payload.JUMP) != 0;
    }

    public Input restrictInput(final Input input, final boolean passenger) {
        if (this.isMovementInputLocked()) return Input.EMPTY;
        final boolean dismountLocked = passenger && (this.inputLockFlags & InputLocksV3Payload.DISMOUNT) != 0;
        return new Input(input.forward(), input.backward(), input.left(), input.right(),
                input.jump() && !this.isJumpInputLocked(), input.shift() && !dismountLocked, input.sprint());
    }

    public void onMovementInputUpdate(final MovementInputUpdateEvent event) {
        event.getInput().keyPresses = this.restrictInput(event.getInput().keyPresses, event.getEntity() != null && event.getEntity().isPassenger());
        if (this.isMovementInputLocked()) {
            ((ClientInputAccessor) event.getInput()).vbu$setMoveVector(Vec2.ZERO);
        }
    }

    public void removeCustomEntity(UUID uuid) {
        customEntityHurtTracker.clear(uuid);
        playerVisualStates.remove(uuid);
        final CustomEntityTicker ticker = cachedCustomEntities.remove(uuid);
        if (ticker != null) {
            ticker.getRenderer().invalidateFrozenMeshes("entity_removed");
        }
    }

    /** 只有已经绑定 VBU 自定义模型的实体才记录受击，原版实体继续使用原版逻辑。 */
    public void markCustomEntityHurt(UUID uuid, long currentTick) {
        if (cachedCustomEntities.containsKey(uuid)) {
            customEntityHurtTracker.markHurt(uuid, currentTick);
        }
    }

    public void markCustomEntityDead(UUID uuid) {
        if (cachedCustomEntities.containsKey(uuid)) {
            customEntityHurtTracker.markDead(uuid);
        }
    }

    @Getter
    private boolean customEntityMotionEnabled;

    @Getter
    private boolean witherSkullMotionEnabled;

    public void handle(final BasePayload payload) {
        var attributionProfiler = net.minecraft.util.profiling.Profiler.get();
        attributionProfiler.push("ec:viabedrockutility/payload/apply");
        try {
        // 能力确认与瞬移不依赖资源包加载，不能排队后重放过时的位置。
        if (payload instanceof CustomEntityMotionPayload motion) {
            if (motion.getAction() == CustomEntityMotionPayload.ENABLE) {
                this.customEntityMotionEnabled = true;
            } else if (motion.getAction() == CustomEntityMotionPayload.ENABLE_WITHER_SKULLS) {
                this.witherSkullMotionEnabled = true;
            } else if ((motion.getAction() == CustomEntityMotionPayload.TELEPORT && this.customEntityMotionEnabled)
                    || (motion.getAction() == CustomEntityMotionPayload.TELEPORT_WITHER_SKULL && this.witherSkullMotionEnabled)) {
                final Minecraft client = Minecraft.getInstance();
                if (client.level != null) {
                    Entity entity = null;
                    for (Entity candidate : client.level.entitiesForRendering()) {
                        if (candidate.getUUID().equals(motion.getUuid())) {
                            entity = candidate;
                            break;
                        }
                    }
                    final boolean supported = motion.getAction() == CustomEntityMotionPayload.TELEPORT
                            ? entity instanceof Interaction : entity instanceof WitherSkull;
                    if (supported && !entity.isRemoved()) {
                        entity.getPositionCodec().setBase(new Vec3(motion.getX(), motion.getY(), motion.getZ()));
                        entity.snapTo(motion.getX(), motion.getY(), motion.getZ(), motion.getYaw(), motion.getPitch());
                        entity.setOnGround(motion.isOnGround());
                    }
                }
            }
            return;
        }
        // Input permission changes must take effect even while resource packs are still loading.
        if (payload instanceof InputLocksPayload inputLocks) {
            this.inputLockFlags = inputLocks.getFlags();
            this.immobile = false;
            return;
        }
        if (payload instanceof InputLocksV3Payload inputLocks) {
            this.inputLockFlags = inputLocks.getFlags();
            this.immobile = inputLocks.isImmobile();
            return;
        }
        if (payload instanceof InputLocksV2Payload inputLocks) {
            this.inputLockFlags = inputLocks.getFlags();
            this.immobile = inputLocks.isImmobile();
            return;
        }
        if (payload instanceof PlayerVisualStatePayload visualState) {
            this.handle(visualState);
            return;
        }

        if (this.packManager != ViaBedrockUtility.getInstance().getPackManager()) {
            this.packManager = ViaBedrockUtility.getInstance().getPackManager();
        }

        boolean skinPayload = payload instanceof BaseSkinPayload || payload instanceof SkinDataPayload
                || payload instanceof CapeDataPayload || payload instanceof SkinAnimationInfoPayload
                || payload instanceof SkinAnimationDataPayload;
        if (!skinPayload && (this.packManager == null || !replayingPending && !pendingPayloads.isEmpty())) {
            if (this.pendingPayloads.size() >= MAX_PENDING) {
                this.pendingPayloads.poll(); // drop oldest to avoid pathological buildup
            }
            this.pendingPayloads.add(payload);
            ViaBedrockUtilityNeoForge.LOGGER.debug("[Payload] Queued {} until PackManager is ready ({} pending)", payload.getClass().getSimpleName(), this.pendingPayloads.size());
            return;
        }

        if (payload instanceof ModelRequestPayload modelRequest) {
            this.handle(modelRequest);
        } else if (payload instanceof BaseSkinPayload baseSkin) {
            skinWork.cancel(baseSkin.getPlayerUuid());
            cachedSkinInfo.remove(baseSkin.getPlayerUuid());
            pendingAnimations.remove(baseSkin.getPlayerUuid());
            try {
                long reservation = SkinImageBudget.reservation(baseSkin.getSkinWidth(), baseSkin.getSkinHeight(),
                        baseSkin.getChunkCount(), baseSkin.getGeometry(), baseSkin.getResourcePatch());
                long transfer = skinDebugLog.begin(baseSkin.getPlayerUuid(), clientTick(), baseSkin.getSkinWidth(),
                        baseSkin.getSkinHeight(), baseSkin.getChunkCount(), geometryIdentifier(baseSkin.getResourcePatch()),
                        baseSkin.getGeometry(), baseSkin.getResourcePatch());
                skinTransfers.put(baseSkin.getPlayerUuid(), transfer);
                var info = new SkinInfo(baseSkin.getGeometry(), baseSkin.getResourcePatch(), baseSkin.getSkinWidth(),
                        baseSkin.getSkinHeight(), baseSkin.getChunkCount(), transfer);
                if (skinWork.offer(baseSkin.getPlayerUuid(), reservation, new SkinInstall(baseSkin.getPlayerUuid(), info), true)) {
                    cachedSkinInfo.put(baseSkin.getPlayerUuid(), info);
                }
            } catch (IllegalArgumentException invalid) { rejectedSkinInputs++; }
        } else if (payload instanceof SkinDataPayload skinData) {
            this.handle(skinData);
        } else if (payload instanceof CapeDataPayload capePayload) {
            ViaBedrockUtilityNeoForge.LOGGER.debug("[Skin] Received cape data for player {}", capePayload.getPlayerUuid());
            this.handle(capePayload);
        } else if (payload instanceof SkinAnimationInfoPayload animInfo) {
            this.handle(animInfo);
        } else if (payload instanceof SkinAnimationDataPayload animData) {
            this.handle(animData);
        } else if (payload instanceof SpawnParticlePayload particlePayload) {
            this.handle(particlePayload);
        } else if (payload instanceof SpawnParticleV2Payload particlePayload) {
            this.handle(particlePayload);
        } else if (payload instanceof AnimatePayload animatePayload) {
            this.handle(animatePayload);
        } else if (payload instanceof PlayerAnimatePayload animatePayload) {
            this.handle(animatePayload);
        }

        } finally { attributionProfiler.pop(); }
    }

    public void handle(final PlayerVisualStatePayload payload) {
        if (payload.getFlags() == 0) {
            this.playerVisualStates.remove(payload.getPlayerUuid());
        } else {
            this.playerVisualStates.put(payload.getPlayerUuid(), payload.getFlags());
        }
    }

    public boolean isPlayerTranslucent(final UUID playerUuid) {
        return (this.playerVisualStates.getOrDefault(playerUuid, 0)
                & PlayerVisualStatePayload.CUSTOM_SPECTATOR) != 0;
    }

    /**
     * Replay payloads that were queued while PackManager was still loading. Called once the resource pack
     * finishes loading (ServerResourcePackLoaderMixin). Runs on the client thread. Replayed payloads go back
     * through handle(BasePayload); since PackManager is now non-null they are dispatched normally rather than
     * re-queued. Each is isolated so one failure does not abort the rest.
     */
    public void flushPendingPayloads() {
        var attributionProfiler = net.minecraft.util.profiling.Profiler.get();
        attributionProfiler.push("ec:viabedrockutility/payload/flush_pending");
        try {
        if (this.pendingPayloads.isEmpty()) {
            return;
        }
        this.packManager = ViaBedrockUtility.getInstance().getPackManager();
        if (this.packManager == null) {
            return; // still not ready, keep the queue for the next attempt
        }
        long start = System.nanoTime();
        int remaining = 16;
        replayingPending = true;
        BasePayload payload;
        while (remaining-- > 0 && System.nanoTime() - start < 2_000_000L
                && (payload = this.pendingPayloads.poll()) != null) {
            try {
                this.handle(payload);
            } catch (final Exception e) {
                ViaBedrockUtilityNeoForge.LOGGER.warn("[Payload] Failed to replay queued {}: {}", payload.getClass().getSimpleName(), e.getMessage());
            }
        }

        } finally { replayingPending = false; attributionProfiler.pop(); }
    }

    public void handle(final ModelRequestPayload payload) {}

    public void handle(final AnimatePayload payload) {}

    public void handle(final PlayerAnimatePayload payload) {}

    public void handle(final SpawnParticlePayload payload) {
        ViaBedrockUtilityNeoForge.LOGGER.debug("[Particle:L4] Handling SpawnParticlePayload: {} at ({}, {}, {}), molang={}", payload.getIdentifier(), payload.getX(), payload.getY(), payload.getZ(), payload.getMolangVarsJson());
        Map<String, Float> molangVars = null;
        final String json = payload.getMolangVarsJson();
        if (json != null && !json.isEmpty()) {
            molangVars = parseMolangVarsJson(json);
        }
        final var emitter = ViaBedrockUtility.getInstance().spawnParticle(
                org.oryxel.viabedrockutility.particle.BedrockParticleRequest.builder(payload.getIdentifier())
                        .position(payload.getX(), payload.getY(), payload.getZ())
                        .variables(molangVars).source("viabedrock-legacy").build()) ? Boolean.TRUE : null;
        ViaBedrockUtilityNeoForge.LOGGER.debug("[Particle:L4] spawnEmitter result: {} (definitions loaded: {})", emitter != null ? "SUCCESS" : "NULL (definition not found)", net.easecation.beparticle.ParticleManager.INSTANCE.getDefinitionCount());
    }

    public void handle(final SpawnParticleV2Payload payload) {
        Map<String, Float> vars = payload.getMolangVarsJson() == null || payload.getMolangVarsJson().isEmpty()
                ? null : parseMolangVarsJson(payload.getMolangVarsJson());
        boolean spawned;
        if (payload.getAnchorKind() == SpawnParticleV2Payload.ENTITY_ANCHOR) {
            spawned = ViaBedrockUtility.getInstance().getParticleRuntime().spawnEntity(
                    payload.getIdentifier(), payload.getOwnerUuid(), payload.getX(), payload.getY(), payload.getZ(), vars);
        } else {
            spawned = ViaBedrockUtility.getInstance().spawnParticle(
                    org.oryxel.viabedrockutility.particle.BedrockParticleRequest.builder(payload.getIdentifier())
                            .position(payload.getX(), payload.getY(), payload.getZ()).variables(vars)
                            .source("viabedrock-v2-world").build());
        }
        ViaBedrockUtilityNeoForge.LOGGER.debug("[Particle:L4] V2 spawn {} result={}", payload.getIdentifier(), spawned);
    }

    /**
     * Parse Bedrock molang variables JSON.
     * Format: [{"name":"variable.color_r","value":{"type":"float","value":1.0}}, ...]
     * Returns a map with variable names stripped of "variable." prefix (e.g. "color_r" -> 1.0f).
     */
    private Map<String, Float> parseMolangVarsJson(String json) {
        try {
            final com.google.gson.JsonElement element = com.google.gson.JsonParser.parseString(json);
            final Map<String, Float> vars = new java.util.HashMap<>();
            if (element.isJsonArray()) {
                // Bedrock format: [{"name":"variable.xxx","value":{"type":"float","value":1.0}}, ...]
                for (final com.google.gson.JsonElement item : element.getAsJsonArray()) {
                    final com.google.gson.JsonObject obj = item.getAsJsonObject();
                    String name = obj.get("name").getAsString();
                    // Strip "variable." prefix for MoLang scope binding
                    if (name.startsWith("variable.")) {
                        name = name.substring("variable.".length());
                    }
                    final com.google.gson.JsonObject valueObj = obj.getAsJsonObject("value");
                    final String type = valueObj.get("type").getAsString();
                    if ("float".equals(type)) {
                        vars.put(name, valueObj.get("value").getAsFloat());
                    }
                    // member_array type is nested, skip for now
                }
            } else if (element.isJsonObject()) {
                // Simple format fallback: {"varName": floatValue, ...}
                for (var entry : element.getAsJsonObject().entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        vars.put(entry.getKey(), entry.getValue().getAsFloat());
                    }
                }
            }
            return vars.isEmpty() ? null : vars;
        } catch (Exception e) {
            ViaBedrockUtilityNeoForge.LOGGER.warn("[Particle] Failed to parse molang vars JSON: {}", json, e);
            return null;
        }
    }

    public void handle(final CapeDataPayload payload) {
        try {
            long bytes = SkinImageBudget.bytes(payload.getWidth(), payload.getHeight());
            if (payload.getCapeData().length != bytes) { rejectedSkinInputs++; return; }
            skinWork.offer(payload.getPlayerUuid(), bytes * 2, new CapeInstall(payload), false);
        } catch (IllegalArgumentException invalid) { rejectedSkinInputs++; }
    }

    private static final List<String> HARDCODED_GEOMETRY_IDENTIFIERS = List.of(
            "geometry.humanoid.custom", "geometry.humanoid.customSlim");

    public void handle(final SkinDataPayload payload) {
        SkinInfo info = cachedSkinInfo.get(payload.getPlayerUuid());
        if (info == null || info.isComplete()) return;
        if (!info.setData(payload.getSkinData(), payload.getChunkPosition())) {
            rejectedSkinInputs++;
            skinWork.cancel(payload.getPlayerUuid());
            return;
        }
        skinDebugLog.chunk(payload.getPlayerUuid(), info.getTransferSequence(), clientTick(),
                payload.getChunkPosition(), payload.getSkinData().length);
    }

    /** Both live payloads and resource-ready replay enqueue here; neither applies a whole skin inline. */
    public void beforeSkinFrame(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event) {
        if (packManager == null) return;
        flushPendingPayloads();
        var profiler = net.minecraft.util.profiling.Profiler.get();
        profiler.push("ec:viabedrockutility/skin/pump");
        try { skinWork.pump(4, 2_000_000L, System::nanoTime); }
        finally { profiler.pop(); }
    }

    /** Commands cannot overtake a pending renderer replacement. A failed/replaced skin retires its commands. */
    protected boolean deferPlayerAnimation(UUID player, long bytes, Runnable command) {
        Long transfer = skinTransfers.get(player);
        if (transfer == null || transfer.equals(installedSkinTransfers.get(player))) return false;
        skinWork.offer(player, bytes, new SkinStage(player) {
            @Override String operation() { return "ec:viabedrockutility/skin/animation_command"; }
            @Override boolean advance() {
                if (installedSkinTransfers.getOrDefault(player, 0L) == this.transfer) command.run();
                return true;
            }
        }, false);
        return true;
    }

    public Map<String, Object> skinWorkStatistics() {
        var result = new LinkedHashMap<String, Object>(skinWork.statistics());
        result.put("rejectedInputs", rejectedSkinInputs);
        return Map.copyOf(result);
    }

    private abstract class SkinStage implements SkinWorkQueue.Work {
        final UUID player;
        final long transfer;
        final long connectionEpoch = ViaBedrockUtility.getInstance().getConnectionEpoch();
        final long deadline = System.nanoTime() + 30_000_000_000L;
        NativeImage image;
        DynamicTexture uploadingTexture;
        int uploadedRows;
        ImageUtil.RgbaConversion pixels;
        int stage;
        SkinStage(UUID player) {
            this.player = player;
            this.transfer = skinTransfers.getOrDefault(player, 0L);
        }
        boolean current() {
            return ViaBedrockUtility.getInstance().isCurrentConnectionEpoch(connectionEpoch)
                    && skinTransfers.getOrDefault(player, 0L) == transfer;
        }
        boolean awaitingData() { return false; }
        boolean expired() { return awaitingData() && System.nanoTime() - deadline >= 0; }
        @Override public final boolean step() {
            if (!current()) return true;
            if (expired()) throw new IllegalStateException("Skin preparation expired");
            var profiler = net.minecraft.util.profiling.Profiler.get();
            profiler.push(operation());
            try { return advance(); }
            finally { profiler.pop(); }
        }
        abstract String operation();
        abstract boolean advance();
        @Override public void failed(RuntimeException failure) { rejectedSkinInputs++; }
        @Override public void close() {
            if (image != null) { image.close(); image = null; }
            if (pixels != null) { pixels.close(); pixels = null; }
            if (uploadingTexture != null) { uploadingTexture.close(); uploadingTexture = null; }
        }
        boolean convert(java.util.function.Supplier<byte[]> rgba, int width, int height) {
            if (pixels == null) pixels = ImageUtil.beginConversion(rgba.get(), width, height);
            if (!pixels.step(16_384)) return false; // At most 64 KiB of pixel writes per player per frame.
            image = pixels.take(); pixels.close(); pixels = null;
            return true;
        }
        /** Prepare an unpublished texture, upload bounded rows, then transfer it to TextureManager. */
        boolean registerTexture(ResourceLocation id) {
            var profiler = net.minecraft.util.profiling.Profiler.get();
            if (uploadingTexture == null) {
                profiler.push("ec:viabedrockutility/skin/create_gpu");
                try {
                    // This constructor creates storage without uploading. setPixels closes its temporary CPU buffer.
                    uploadingTexture = new DynamicTexture(id::toString, image.getWidth(), image.getHeight(), false);
                    uploadingTexture.setPixels(image);
                    image = null;
                } finally { profiler.pop(); }
                return false;
            }
            NativeImage source = uploadingTexture.getPixels();
            int rows = Math.min(source.getHeight() - uploadedRows, 1_048_576 / (source.getWidth() * 4));
            profiler.push("ec:viabedrockutility/skin/upload_rows");
            try { writeSkinRows(uploadingTexture, source, uploadedRows, rows); }
            finally { profiler.pop(); }
            uploadedRows += rows;
            if (uploadedRows != source.getHeight()) return false;
            Minecraft.getInstance().getTextureManager().register(id, uploadingTexture);
            uploadingTexture = null; // TextureManager now owns both GPU and CPU storage.
            return true;
        }
    }

    protected void writeSkinRows(DynamicTexture target, NativeImage source, int y, int rows) {
        com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                target.getTexture(), source, 0, 0, 0, y, source.getWidth(), rows, 0, y);
    }

    private final class SkinInstall extends SkinStage {
        private final SkinInfo info;
        private final ResourceLocation identifier;
        private String requiredGeometry;
        private boolean slim;
        private BedrockGeometryModel geometry;
        private PlayerModel model;
        private CustomPlayerRenderer renderer;
        private PackManager preparedPacks;
        SkinInstall(UUID player, SkinInfo info) {
            super(player); this.info = info;
            identifier = ResourceLocation.fromNamespaceAndPath(ViaBedrockUtilityNeoForge.MOD_ID, player.toString());
        }
        @Override boolean awaitingData() { return !info.isComplete(); }
        @Override public boolean ready() { return expired() || !current() || info.isComplete(); }
        @Override String operation() {
            return switch (stage) {
                case 0 -> "ec:viabedrockutility/skin/prepare_pixels";
                case 1 -> "ec:viabedrockutility/skin/parse_geometry";
                case 2 -> "ec:viabedrockutility/skin/prepare_model";
                case 3 -> "ec:viabedrockutility/skin/prepare_renderer";
                case 4 -> "ec:viabedrockutility/skin/prepare_animation";
                default -> "ec:viabedrockutility/skin/install";
            };
        }
        @Override boolean advance() {
            if (stage > 2 && preparedPacks != packManager) {
                stage = 2; model = null; renderer = null; // Retain decoded pixels, rebuild resource-bound state.
                return false;
            }
            switch (stage++) {
                case 0 -> {
                    if (!convert(() -> {
                        byte[] data = info.getData();
                        PlayerSkinTextureCompat.repairMissingLeftLimbs(data, info.getWidth(), info.getHeight());
                        return data;
                    }, info.getWidth(), info.getHeight())) { stage--; return false; }
                    cachedSkinInfo.remove(player, info);
                }
                case 1 -> {
                    requiredGeometry = geometryIdentifier(info.getResourcePatch());
                    slim = requiredGeometry.startsWith("geometry.humanoid.customSlim");
                    if (!info.getGeometryRaw().isEmpty()) {
                        try {
                            var geometries = BedrockGeometryModel.fromJson(JsonParser.parseString(info.getGeometryRaw()).getAsJsonObject());
                            if (!geometries.isEmpty()) {
                                geometry = geometries.getFirst();
                                for (var candidate : geometries) if (candidate.getIdentifier().equals(requiredGeometry)) {
                                    geometry = candidate; break;
                                }
                            }
                        } catch (RuntimeException ignored) { /* Retain existing bundled-player fallback. */ }
                    }
                    if (geometry == null && HARDCODED_GEOMETRY_IDENTIFIERS.stream().noneMatch(
                            id -> id.equals(requiredGeometry) || requiredGeometry.startsWith(id + "."))) {
                        throw new IllegalArgumentException("Unsupported skin geometry");
                    }
                }
                case 2 -> {
                    preparedPacks = packManager;
                    boolean bundled = HARDCODED_GEOMETRY_IDENTIFIERS.stream().anyMatch(
                            id -> id.equals(requiredGeometry) || requiredGeometry.startsWith(id + "."));
                    try { if (geometry != null) model = (PlayerModel) GeometryUtil.buildModel(geometry, true, slim); }
                    catch (RuntimeException failure) { if (!bundled) throw failure; }
                    if (model == null) {
                        if (!bundled) throw new IllegalArgumentException("Unsupported skin geometry");
                        model = BundledPlayerModelFactory.create(slim);
                    }
                }
                case 3 -> renderer = new CustomPlayerRenderer(EntityRendererContextUtil.build(Minecraft.getInstance()), model, slim, identifier);
                case 4 -> applyAnimationOverrides(player, renderer, info.getResourcePatch(), preparedPacks);
                default -> {
                    // Prepare fallible lookups first. Publish texture and model together on the render thread.
                    var client = Minecraft.getInstance();
                    var entry = client.getConnection() == null ? null : client.getConnection().getPlayerInfo(player);
                    PlayerSkinBuilder builder = entry == null ? null : new PlayerSkinBuilder(entry.getSkin());
                    if (builder != null) { builder.texture = identifier; builder.model = slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE; }
                    var cached = new CachedPlayerSkin(identifier, slim, info.getGeometryRaw(), info.getResourcePatch());
                    if (!registerTexture(identifier)) return false;
                    cachedPlayerRenderers.put(player, renderer);
                    cachedPlayerSkins.put(player, cached);
                    installedSkinTransfers.put(player, transfer);
                    if (entry != null) ((PlayerSkinFieldAccessor) entry).setPlayerSkin(builder::build);
                    skinDebugLog.installed(player, transfer, clientTick(), requiredGeometry, slim, renderer, model, identifier.toString());
                    return true;
                }
            }
            return false;
        }
        @Override public void failed(RuntimeException failure) {
            super.failed(failure);
            skinDebugLog.rejected(player, transfer, clientTick(), failure.getClass().getSimpleName());
            skinWork.cancel(player); // Dependent overlays must not attach to an older installed skin.
        }
        @Override public void close() { super.close(); cachedSkinInfo.remove(player, info); }
    }

    private final class CapeInstall extends SkinStage {
        private final CapeDataPayload payload;
        CapeInstall(CapeDataPayload payload) { super(payload.getPlayerUuid()); this.payload = payload; }
        @Override String operation() { return stage == 0 ? "ec:viabedrockutility/skin/prepare_pixels" : "ec:viabedrockutility/skin/cape_apply"; }
        @Override boolean advance() {
            if (stage++ == 0) {
                if (!convert(payload::getCapeData, payload.getWidth(), payload.getHeight())) stage--;
                return false;
            }
            var client = Minecraft.getInstance();
            var entry = client.getConnection() == null ? null : client.getConnection().getPlayerInfo(player);
            var builder = entry == null ? null : new PlayerSkinBuilder(entry.getSkin());
            if (builder != null) builder.capeTexture = payload.getIdentifier();
            if (!registerTexture(payload.getIdentifier())) return false;
            cachedPlayerCapes.put(player, payload.getIdentifier());
            if (entry != null) ((PlayerSkinFieldAccessor) entry).setPlayerSkin(builder::build);
            return true;
        }
    }

    @Getter
    public static class SkinInfo {
        private final String geometryRaw, resourcePatch;
        private final int width, height;
        private final byte[][] skinData;
        private final long transferSequence;
        private long receivedBytes;
        private int receivedChunks;

        public SkinInfo(String geometryRaw, String resourcePatch, int width, int height,
                        int chunkCount, long transferSequence) {
            this.geometryRaw = geometryRaw;
            this.resourcePatch = resourcePatch;
            this.skinData = new byte[chunkCount][];
            this.width = width;
            this.height = height;
            this.transferSequence = transferSequence;
        }
        /**
         * Should the skin data be sent to us through multiple plugin messages, assemble it.
         */
        public byte[] getData() {
            if (skinData.length == 1) {
                // No concatenation needed
                return skinData[0];
            }

            int totalLength = 0;
            for (byte[] data : skinData) {
                totalLength += data.length;
            }
            byte[] totalData = new byte[totalLength];
            int currentIndex = 0;
            for (byte[] currentData : skinData) {
                // Copy all arrays to one array
                System.arraycopy(currentData, 0, totalData, currentIndex, currentData.length);
                currentIndex += currentData.length;
            }
            return totalData;
        }

        public boolean setData(byte[] data, int chunk) {
            if (chunk < 0 || chunk >= skinData.length || data == null) return false;
            byte[] old = skinData[chunk];
            long next = receivedBytes - (old == null ? 0 : old.length) + data.length;
            long expected = (long) width * height * 4;
            if (next > expected) return false;
            if (old == null) receivedChunks++;
            receivedBytes = next; skinData[chunk] = data;
            return receivedChunks != skinData.length || receivedBytes == expected;
        }

        public boolean isComplete() {
            return receivedChunks == skinData.length && receivedBytes == (long) width * height * 4;
        }
    }

    public void removePlayerCache(UUID uuid) {
        skinWork.cancel(uuid);
        skinTransfers.remove(uuid);
        installedSkinTransfers.remove(uuid);
        skinDebugLog.removed(uuid, clientTick(), "removePlayerCache");
        cachedPlayerRenderers.remove(uuid);
        cachedPlayerCapes.remove(uuid);
        cachedPlayerSkins.remove(uuid);
        cachedSkinInfo.remove(uuid);
        pendingAnimations.remove(uuid);
    }

    @Getter
    public static class CachedPlayerSkin {
        private final ResourceLocation textureId;
        private final boolean slim;
        private final String geometryRaw;
        private final String resourcePatch;

        public CachedPlayerSkin(ResourceLocation textureId, boolean slim, String geometryRaw, String resourcePatch) {
            this.textureId = textureId;
            this.slim = slim;
            this.geometryRaw = geometryRaw;
            this.resourcePatch = resourcePatch;
        }
    }

    public void handle(final SkinAnimationInfoPayload payload) {
        try {
            long reservation = SkinImageBudget.reservation(payload.getWidth(), payload.getHeight(), payload.getChunkCount(), "", "");
            int frames = (int) payload.getFrames();
            if (!Float.isFinite(payload.getFrames()) || payload.getFrames() != frames || frames < 1
                    || frames > payload.getHeight() || payload.getHeight() % frames != 0
                    || payload.getAnimIndex() < 0 || payload.getAnimIndex() >= 16
                    || payload.getType() < 1 || payload.getType() > 3) {
                rejectedSkinInputs++; return;
            }
            var pending = new PendingAnimation(payload.getAnimIndex(), payload.getType(), frames, payload.getExpression(),
                    payload.getWidth(), payload.getHeight(), payload.getChunkCount());
            if (skinWork.offer(payload.getPlayerUuid(), reservation, new AnimationInstall(payload.getPlayerUuid(), pending), false)) {
                pendingAnimations.computeIfAbsent(payload.getPlayerUuid(), key -> new ConcurrentHashMap<>())
                        .put(payload.getAnimIndex(), pending);
            }
        } catch (IllegalArgumentException invalid) { rejectedSkinInputs++; }
    }

    public void handle(final SkinAnimationDataPayload payload) {
        var animations = pendingAnimations.get(payload.getPlayerUuid());
        var pending = animations == null ? null : animations.get(payload.getAnimIndex());
        if (pending != null && !pending.isComplete() && !pending.setData(payload.getData(), payload.getChunkPosition())) {
            rejectedSkinInputs++;
            animations.remove(payload.getAnimIndex(), pending); // Queued owner releases its reservation next frame.
        }
    }

    private final class AnimationInstall extends SkinStage {
        private final PendingAnimation pending;
        private BedrockGeometryModel geometry;
        private PlayerModel model;
        private CustomPlayerRenderer renderer;
        private CachedPlayerSkin skin;
        private AnimatedSkinOverlay overlay;
        AnimationInstall(UUID player, PendingAnimation pending) { super(player); this.pending = pending; }
        private boolean replaced() {
            var map = pendingAnimations.get(player);
            return map == null || map.get(pending.animIndex) != pending;
        }
        @Override boolean awaitingData() { return !pending.isComplete(); }
        @Override public boolean ready() { return expired() || !current() || replaced() || pending.isComplete(); }
        @Override String operation() {
            return switch (stage) {
                case 0 -> "ec:viabedrockutility/skin/animation_geometry";
                case 1 -> "ec:viabedrockutility/skin/prepare_model";
                case 2 -> "ec:viabedrockutility/skin/prepare_pixels";
                default -> "ec:viabedrockutility/skin/animation_apply";
            };
        }
        @Override boolean advance() {
            if (replaced()) return true;
            switch (stage++) {
                case 0 -> {
                    skin = cachedPlayerSkins.get(player);
                    var currentRenderer = cachedPlayerRenderers.get(player);
                    if (skin == null || !(currentRenderer instanceof CustomPlayerRenderer custom)
                            || installedSkinTransfers.getOrDefault(player, 0L) != transfer) return true;
                    renderer = custom;
                    String key = switch (pending.type) {
                        case 1 -> "animated_face"; case 2 -> "animated_32x32"; default -> "animated_128x128";
                    };
                    var patch = JsonParser.parseString(skin.getResourcePatch()).getAsJsonObject().getAsJsonObject("geometry");
                    if (patch == null || !patch.has(key)) return true;
                    String identifier = patch.get(key).getAsString();
                    var geometries = BedrockGeometryModel.fromJson(JsonParser.parseString(skin.getGeometryRaw()).getAsJsonObject());
                    for (var candidate : geometries) if (candidate.getIdentifier().equals(identifier)) { geometry = candidate; break; }
                    if (geometry == null) return true;
                }
                case 1 -> model = (PlayerModel) GeometryUtil.buildModel(geometry, true, skin.isSlim(), geometry.getIdentifier());
                case 2 -> { if (!convert(pending::getData, pending.width, pending.height)) stage--; }
                default -> {
                    if (cachedPlayerRenderers.get(player) != renderer) return true;
                    var textureId = ResourceLocation.fromNamespaceAndPath(ViaBedrockUtilityNeoForge.MOD_ID, player + "/anim_" + pending.animIndex);
                    if (overlay == null) overlay = new AnimatedSkinOverlay(model, textureId, pending.type, pending.totalFrames,
                            pending.expression, pending.height, pending.height / pending.totalFrames);
                    if (!registerTexture(textureId)) return false;
                    renderer.setOverlay(pending.animIndex, overlay);
                    return true;
                }
            }
            return false;
        }
        @Override public void close() {
            super.close();
            var animations = pendingAnimations.get(player);
            if (animations != null) {
                animations.remove(pending.animIndex, pending);
                if (animations.isEmpty()) pendingAnimations.remove(player, animations);
            }
        }
    }

    public void tickAnimationOverlays() {
        for (EntityRenderer<?, ?> renderer : cachedPlayerRenderers.values()) {
            if (renderer instanceof CustomPlayerRenderer customRenderer) {
                customRenderer.tickOverlays();
            }
        }
    }

    private static void applyAnimationOverrides(UUID playerUuid, CustomPlayerRenderer renderer,
                                                String resourcePatch, PackManager manager) {
        if (manager == null) {
            return;
        }
        try {
            final Map<String, String> overrides = new LinkedHashMap<>();
            if (resourcePatch != null && !resourcePatch.isBlank()) {
                final JsonObject patch = JsonParser.parseString(resourcePatch).getAsJsonObject();
                if (patch.has("animations")) {
                    for (final var entry : patch.getAsJsonObject("animations").entrySet()) {
                        overrides.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
            if (renderer.setPlayerAnimationRuntime(manager, overrides)) {
                ViaBedrockUtilityNeoForge.LOGGER.debug(
                        "[Skin] Created Bedrock player runtime with {} alias override(s) for {}: {}",
                        overrides.size(), playerUuid, overrides.keySet());
            } else {
                ViaBedrockUtilityNeoForge.LOGGER.warn(
                        "[Skin] Current resource-pack generation has no usable minecraft:player runtime for {}; using vanilla poses",
                        playerUuid);
            }
        } catch (final Exception e) {
            renderer.clearPlayerAnimationRuntime();
            ViaBedrockUtilityNeoForge.LOGGER.warn(
                    "[Skin] Failed to create Bedrock player runtime for {}; using vanilla poses",
                    playerUuid, e);
        }
    }

    public EntityRenderer<?, ?> cachedPlayerRenderer(UUID playerUuid) {
        return cachedPlayerRenderers.get(playerUuid);
    }

    private static long clientTick() {
        final Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? -1L : minecraft.player.tickCount;
    }

    private static String geometryIdentifier(String resourcePatch) {
        try {
            final JsonObject patch = JsonParser.parseString(resourcePatch).getAsJsonObject();
            final JsonObject geometries = patch.getAsJsonObject("geometry");
            return geometries == null || !geometries.has("default")
                    ? "" : geometries.get("default").getAsString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    @Getter
    private static class PendingAnimation {
        private final int animIndex;
        private final int type;
        private final int totalFrames;
        private final int expression;
        private final int width;
        private final int height;
        private final byte[][] data;
        private int receivedChunks;
        private long receivedBytes;

        PendingAnimation(int animIndex, int type, int totalFrames, int expression, int width, int height, int chunkCount) {
            this.animIndex = animIndex;
            this.type = type;
            this.totalFrames = totalFrames;
            this.expression = expression;
            this.width = width;
            this.height = height;
            this.data = new byte[chunkCount][];
        }

        public boolean setData(byte[] chunkData, int chunkPosition) {
            if (chunkPosition < 0 || chunkPosition >= data.length || chunkData == null) return false;
            byte[] old = data[chunkPosition];
            long next = receivedBytes - (old == null ? 0 : old.length) + chunkData.length;
            if (next > (long) width * height * 4) return false;
            if (old == null) receivedChunks++;
            receivedBytes = next; data[chunkPosition] = chunkData;
            return receivedChunks != data.length || receivedBytes == (long) width * height * 4;
        }

        public boolean isComplete() { return receivedChunks == data.length && receivedBytes == (long) width * height * 4; }

        public byte[] getData() {
            if (data.length == 1) return data[0];
            int totalLength = 0;
            for (byte[] chunk : data) totalLength += chunk.length;
            byte[] result = new byte[totalLength];
            int offset = 0;
            for (byte[] chunk : data) {
                System.arraycopy(chunk, 0, result, offset, chunk.length);
                offset += chunk.length;
            }
            return result;
        }
    }
}
