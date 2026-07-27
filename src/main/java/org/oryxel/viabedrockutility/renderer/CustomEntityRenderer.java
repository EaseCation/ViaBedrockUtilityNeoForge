package org.oryxel.viabedrockutility.renderer;

import lombok.Getter;
import lombok.Setter;
import net.easecation.bedrockmotion.animator.Animator;
import net.easecation.bedrockmotion.controller.AnimationControllerInstance;
import net.easecation.bedrockmotion.mocha.LayeredScope;
import net.easecation.bedrockmotion.mocha.MoLangEngine;
import net.easecation.bedrockmotion.pack.definitions.AnimationDefinitions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;
import org.cube.converter.data.bedrock.controller.BedrockRenderController;
import org.joml.Matrix4f;
import org.oryxel.viabedrockutility.adapter.McBoneModel;
import org.oryxel.viabedrockutility.config.LodConfig;
import org.oryxel.viabedrockutility.entity.CustomEntityTicker;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import org.oryxel.viabedrockutility.material.data.Material;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;
import org.oryxel.viabedrockutility.renderer.model.CustomEntityModel;
import team.unnamed.mocha.parser.ast.Expression;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.Value;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class CustomEntityRenderer<T extends Entity> extends EntityRenderer<T, CustomEntityRenderer.CustomEntityRenderState> {
    private static final Value VAL_TRUE = Value.of(1.0);
    private static final Value VAL_FALSE = Value.of(0.0);

    private final Map<String, Animator> animators = new ConcurrentHashMap<>();
    private final Map<CustomEntityModel<?>, McBoneModel> boneModelCache = new IdentityHashMap<>();
    private final Map<Model, FrozenMeshEntry> frozenEntries = new IdentityHashMap<>();
    private final Map<Model, String> frozenFailures = new IdentityHashMap<>();
    private final Map<Model, Boolean> frozenStaticGeometrySafe = new IdentityHashMap<>();
    private final FrozenMeshStateController frozenState = new FrozenMeshStateController();
    private boolean hasRetainedAnimatedPose;
    // Resolved once per entity model: each non-wildcard part_visibility rule pre-mapped to its target
    // ModelPart[], plus a snapshot of allParts(). Lets applyPartVisibility iterate arrays every frame
    // instead of doing parsedPV.entrySet() iteration + partsByName.get() (HashMap get/hashCode) and
    // entityModel.allParts() (which builds a fresh List via Stream.collect each call). Same 1:1
    // entity-model ↔ parsed-visibility pairing as boneModelCache, so the model is a safe cache key.
    private final Map<CustomEntityModel<?>, ResolvedPartVisibility> partVisibilityCache = new IdentityHashMap<>();
    private static final ModelPart[] EMPTY_MODEL_PARTS = new ModelPart[0];
    private final LayeredScope reusableFrameScope = new LayeredScope(Scope.create());
    // Reused across frames instead of allocating a new MutableObjectBinding (and its backing
    // CaseInsensitiveStringHashMap, which lowercases + double-puts every key on every set) every frame.
    // Entity flags and nullable ids are updated only when their value changes, so no clear() or full
    // flag-map rewrite is needed. This was the one buildFrameScope allocation left after the
    // earlier GC pass; it drove ~2.6% toLowerCase + a chunk of putVal/hashCode on the render thread.
    private final MutableObjectBinding reusableQueryBinding = new MutableObjectBinding();
    private final CustomEntityTicker.EntityQueryState reusableEntityQueryState =
            new CustomEntityTicker.EntityQueryState();

    // Cached max visible-bounds across models (recomputed only when the model set size changes),
    // avoiding the repeated model-list scan in getVisualBoundingBox. The AABB itself is still
    // allocated per call — MC's AABB is immutable and has no in-place setter, so it cannot be reused.
    private int boundsCacheModelsSize = -1;
    private float cachedMaxHalfWidth = 0.5f;
    private float cachedMaxHeight = 2.0f;
    private float cachedOffsetY = 1.0f;

    private final CustomEntityTicker ticker;
    private final List<Model> models;

    // Bedrock-style head/body rotation simulation (per-entity)
    private float smoothedHeadYaw;
    private float smoothedHeadPitch;
    private float simulatedBodyYaw;
    private float lastSignificantHeadYaw;  // last head yaw that triggered delay reset
    private long headStableStartMs;         // when head last became "stable"
    private boolean rotationInitialized;

    // LimbAnimator-style movement tracking (per-frame accumulation + per-tick smoothing)
    private double lastPosX, lastPosY, lastPosZ;
    private float tickHorizDist;
    private float tickVertDist;
    private int lastAge = -1;
    private float limbSpeed;
    private float limbDistance;
    private float limbVerticalSpeed;

    public CustomEntityRenderer(final CustomEntityTicker ticker, final List<Model> models, EntityRendererProvider.Context context) {
        super(context);
        this.models = models;
        this.ticker = ticker;
    }

    // Use identity hash as initial offset so different entities' LOD frames are evenly distributed
    private int renderFrameCounter = System.identityHashCode(this);

    @Override
    public void render(CustomEntityRenderState state, PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
        // Distance-based render culling: completely skip rendering for very distant entities
        if (LodConfig.getInstance().shouldSkipRender(state.getDistanceFromCamera())) {
            return;
        }

        this.renderFrameCounter++;

        LodConfig config = LodConfig.getInstance();
        FrozenMeshState previousFrozenState = this.frozenState.state();
        FrozenMeshState currentFrozenState = this.frozenState.update(
                config.isFrozenMeshEnabled(), state.getDistanceFromCamera(),
                config.getFrozenMeshEnterDistance(), config.getFrozenMeshExitDistance());
        if (currentFrozenState == FrozenMeshState.DYNAMIC
                && previousFrozenState != FrozenMeshState.DYNAMIC) {
            this.invalidateFrozenMeshes("near_transition");
        }
        boolean useFrozenPose = currentFrozenState != FrozenMeshState.DYNAMIC;
        boolean initializeFrozenPose = this.frozenState.requiresInitialPose(this.hasRetainedAnimatedPose);

        // Distance-based LOD: skip animation computation for distant entities (configurable)
        boolean shouldAnimate = initializeFrozenPose || (!useFrozenPose
                && config.shouldAnimate(state.getDistanceFromCamera(), this.renderFrameCounter));

        // Per-frame animation budget: distance LOD doesn't throttle near entities, so a dense lobby would
        // animate every one of them every frame. Cap the number of full-rate animations per frame; entities
        // that exhaust the budget fall back to a staggered cadence (renderFrameCounter is identityHashCode-
        // seeded, spreading the throttled updates across frames) so they still animate, just less often.
        if (shouldAnimate && !initializeFrozenPose && !AnimationBudget.tryAcquire()) {
            final int interval = config.getAnimationThrottleInterval();
            shouldAnimate = interval <= 1 || (this.renderFrameCounter % interval == 0);
        }

        final Scope frameScope;
        if (shouldAnimate) {
            // Build per-frame scope with all query bindings, execute pre_animation, set up animators
            frameScope = this.buildFrameScope(state);
            this.ticker.runPreAnimationTask(frameScope);
            this.animators.values().forEach(a -> a.setBaseScope(frameScope));
            this.evaluateAnimationConditions(frameScope);

            // Tick animation controllers (evaluate transitions, update blend weights)
            for (AnimationControllerInstance ci : this.ticker.getControllerInstances()) {
                ci.setBaseScope(frameScope);
                ci.tick(frameScope);
            }
        } else {
            frameScope = null;
        }

        float s = (this.ticker.getScale() != null) ? this.ticker.getScale() : 1.0F;
        for (Model model : this.models) {
            matrices.pushPose();

            this.setupTransforms(state, matrices);
            matrices.scale(-s, -s, s);
            matrices.translate(0.0F, -1.501F, 0.0F);

            if (shouldAnimate) {
                final McBoneModel boneModel = boneModelCache.computeIfAbsent(model.model(), McBoneModel::new);

                // Reset all bones to default pose before additive animation blending
                boneModel.resetAllBones();

                this.animators.values().forEach(animator -> {
                    try {
                        animator.animate(boneModel);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                // Apply animation controller animations
                for (AnimationControllerInstance ci : this.ticker.getControllerInstances()) {
                    ci.animate(boneModel);
                }

                // Apply part_visibility
                if (model.parsedPartVisibility() != null && !model.parsedPartVisibility().isEmpty()) {
                    applyPartVisibility(model.model(), model.parsedPartVisibility(), frameScope);
                }
                this.hasRetainedAnimatedPose = true;
            }

            try {
                Material.MaterialInfo.Variant skinningColor = model.material.info().getVariants().get("skinning_color");
                RenderType renderType = skinningColor.build().apply(model.texture);
                if (renderType != null) {
                    // ignore_lighting / emissive -> render flat & fully bright like Bedrock. FULL_BRIGHT
                    // maxes the lightmap, but Minecraft's entity shader also applies normal-based
                    // directional shading (top bright, bottom dark) that the lightmap can't override;
                    // FlatNormalVertexConsumer forces all normals up to remove it.
                    boolean flatLight = (model.controller() != null && model.controller().ignoreLighting())
                            || skinningColor.getDefines().contains("USE_EMISSIVE");

                    int effectiveLight = light;
                    if (flatLight) {
                        effectiveLight = LightTexture.FULL_BRIGHT;
                    } else if (model.controller() != null && model.controller().lightColorMultiplier() != 1.0F) {
                        effectiveLight = scaleLight(light, model.controller().lightColorMultiplier());
                    }

                    boolean queuedFrozen = false;
                    if (useFrozenPose) {
                        VbuRenderMetrics.recordFrozenCandidate();
                        queuedFrozen = this.tryQueueFrozen(
                                model, renderType, vertexConsumers, effectiveLight, flatLight,
                                matrices.last().pose());
                    }
                    if (!queuedFrozen) {
                        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(renderType);
                        // Signal flat normals without wrapping the consumer, preserving Sodium's writer.
                        VbuCompileScratch.FLAT_NORMAL = flatLight;
                        model.model.renderToBuffer(
                                matrices, vertexConsumer, effectiveLight, OverlayTexture.pack(0, 10));
                    }
                }
            } catch (StackOverflowError soe) {
                ViaBedrockUtilityNeoForge.LOGGER.error(
                        "[VBU] StackOverflow rendering model! key='{}', geometry='{}', texture='{}'",
                        model.key(), model.geometry(), model.texture());
                dumpModelHierarchy(model.model().root(), "", 0);
            } catch (Exception e) {
                ViaBedrockUtilityNeoForge.LOGGER.debug("[Render] Error rendering model key={}, texture={}", model.key(), model.texture(), e);
            } finally {
                // Always clear the flat-normal flag after each model so it never leaks into the next one
                // (which may not be emissive). Render-thread only, so no synchronization needed.
                VbuCompileScratch.FLAT_NORMAL = false;
            }

            matrices.popPose();
        }
        if (this.frozenState.state() == FrozenMeshState.BAKE_PENDING) {
            this.frozenState.bakingComplete();
        }
        super.render(state, matrices, vertexConsumers, light);
    }

    private boolean tryQueueFrozen(Model model, RenderType renderType, MultiBufferSource vertexConsumers,
                                   int effectiveLight, boolean flatLight, Matrix4f rootPose) {
        String priorFailure = this.frozenFailures.get(model);
        if (priorFailure != null) {
            VbuRenderMetrics.recordFrozenFallback(priorFailure, 1);
            return false;
        }
        if (!FrozenMeshEligibility.isRenderContextEligible(
                        renderType, model.texture(), vertexConsumers)
                || !this.frozenStaticGeometrySafe.computeIfAbsent(
                        model, ignored -> !FrozenMeshEligibility.hasDynamicUv(model.model()))) {
            this.frozenFailures.put(model, "eligibility");
            VbuRenderMetrics.recordFrozenFallback("eligibility", 1);
            return false;
        }

        FrozenMeshEntry entry = this.frozenEntries.get(model);
        if (entry != null && entry.isFailed()) {
            this.frozenEntries.remove(model);
            this.frozenFailures.put(model, "draw_error");
            return false;
        }
        if (entry != null && (!entry.isValid()
                || entry.packedLight() != effectiveLight
                || entry.renderType() != renderType)) {
            if (entry.isValid()) {
                FrozenEntityMeshCache.global().remove(entry,
                        entry.packedLight() != effectiveLight ? "light" : "material");
            }
            this.frozenEntries.remove(model);
            entry = null;
            this.frozenState.requestBake();
        }

        if (entry == null) {
            entry = this.bakeFrozenMesh(model, renderType, effectiveLight, flatLight);
            if (entry == null) {
                return false;
            }
            this.frozenEntries.put(model, entry);
        } else {
            FrozenEntityMeshCache.global().touch(entry);
            VbuRenderMetrics.recordFrozenCacheHit();
        }

        if (!FrozenMeshDrawQueue.enqueue(
                entry, rootPose, model.model(), effectiveLight, flatLight)) {
            VbuRenderMetrics.recordFrozenFallback("draw_error", 1);
            return false;
        }
        return true;
    }

    private FrozenMeshEntry bakeFrozenMesh(Model model, RenderType renderType,
                                           int effectiveLight, boolean flatLight) {
        try (ByteBufferBuilder allocation = new ByteBufferBuilder(renderType.bufferSize())) {
            BufferBuilder builder = new BufferBuilder(allocation, renderType.mode(), renderType.format());
            PoseStack identityPose = new PoseStack();
            VbuCompileScratch.FLAT_NORMAL = flatLight;
            model.model().renderToBuffer(
                    identityPose, builder, effectiveLight, OverlayTexture.pack(0, 10));
            try (MeshData mesh = builder.build()) {
                if (mesh == null || mesh.drawState().vertexCount() < 128) {
                    this.frozenFailures.put(model, "too_small");
                    VbuRenderMetrics.recordFrozenFallback("too_small", 1);
                    return null;
                }
                FrozenMeshBackend.Handle handle = FrozenMeshDrawQueue.upload(
                        model.key() + "/" + model.geometry(), mesh.vertexBuffer());
                FrozenMeshEntry entry = new FrozenMeshEntry(
                        handle, renderType, mesh.drawState().vertexCount(),
                        mesh.drawState().indexCount(), mesh.drawState().mode(), effectiveLight);
                if (!FrozenEntityMeshCache.global().add(entry)) {
                    this.frozenFailures.put(model, "cache_full");
                    VbuRenderMetrics.recordFrozenFallback("cache_full", 1);
                    return null;
                }
                VbuRenderMetrics.recordFrozenUpload(entry.sizeBytes());
                return entry;
            }
        } catch (Throwable error) {
            this.frozenFailures.put(model, "upload_error");
            VbuRenderMetrics.recordFrozenFallback("upload_error", 1);
            ViaBedrockUtilityNeoForge.LOGGER.debug(
                    "[FrozenMesh] Failed to bake model key={}, geometry={}",
                    model.key(), model.geometry(), error);
            return null;
        } finally {
            VbuCompileScratch.FLAT_NORMAL = false;
        }
    }

    public void invalidateFrozenMeshes(String reason) {
        for (FrozenMeshEntry entry : this.frozenEntries.values()) {
            if (entry.isValid()) {
                FrozenEntityMeshCache.global().remove(entry, reason);
            }
        }
        this.frozenEntries.clear();
        this.frozenFailures.clear();
        this.frozenStaticGeometrySafe.clear();
        if ("model_change".equals(reason)) {
            this.hasRetainedAnimatedPose = false;
        }
        this.frozenState.reset();
    }

    // Bedrock render controller "light_color_multiplier": a scalar applied to the light color
    // reaching the entity (>1 brightens beyond ambient, <1 darkens; default 1.0). Java has no RGB
    // light color - lighting arrives as a packed lightmap coord (block in low 16 bits, sky in high
    // 16, each a level<<4 in 0..240). Approximate by scaling both coord components by the multiplier
    // and clamping to the legal 0..240 (0xF0) range, then repacking. Independent of LightTexture
    // unpack helpers to avoid level-vs-coord ambiguity.
    private static int scaleLight(final int packedLight, final float multiplier) {
        final int block = packedLight & 0xFFFF;
        final int sky = (packedLight >> 16) & 0xFFFF;
        final int scaledBlock = Mth.clamp(Math.round(block * multiplier), 0, 0xF0);
        final int scaledSky = Mth.clamp(Math.round(sky * multiplier), 0, 0xF0);
        return (scaledSky << 16) | scaledBlock;
    }

    @Override
    public boolean shouldRender(T entity, Frustum frustum, double x, double y, double z) {
        if (!frustum.isVisible(getVisualBoundingBox(entity))) return false;
        double d = 64.0F * Entity.getViewScale();
        return entity.distanceToSqr(x, y, z) <= d * d;
    }

    /**
     * Constructs a bounding box from geometry visible_bounds (width/height/offset) and entity scale,
     * rather than the entity's collision box which may be much smaller than the rendered model.
     */
    private net.minecraft.world.phys.AABB getVisualBoundingBox(T entity) {
        float scale = (this.ticker.getScale() != null) ? this.ticker.getScale() : 1.0f;

        // Cache the max visible bounds across models; recompute only when the model set changes.
        if (this.boundsCacheModelsSize != this.models.size()) {
            this.boundsCacheModelsSize = this.models.size();
            float maxHalfWidth = 0.5f;
            float maxHeight = 2.0f;
            float offsetY = 1.0f;
            for (Model model : this.models) {
                var vb = model.visibleBounds();
                if (vb != null) {
                    maxHalfWidth = Math.max(maxHalfWidth, vb.width() / 2.0f);
                    maxHeight = Math.max(maxHeight, vb.height());
                    offsetY = vb.offsetY();
                }
            }
            this.cachedMaxHalfWidth = maxHalfWidth;
            this.cachedMaxHeight = maxHeight;
            this.cachedOffsetY = offsetY;
        }

        float maxHalfWidth = this.cachedMaxHalfWidth * scale;
        float maxHeight = this.cachedMaxHeight * scale;
        float offsetY = this.cachedOffsetY * scale;

        double ex = entity.getX();
        double ey = entity.getY();
        double ez = entity.getZ();
        double cy = ey + offsetY; // center Y of the visible bounds
        return new net.minecraft.world.phys.AABB(
                ex - maxHalfWidth, cy - maxHeight / 2.0, ez - maxHalfWidth,
                ex + maxHalfWidth, cy + maxHeight / 2.0, ez + maxHalfWidth
        );
    }

    @Override
    public void extractRenderState(T entity, CustomEntityRenderState state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        state.setCustomRenderer(this);
        // Update entity position for particle spawning
        this.ticker.setEntityPosition(new org.joml.Vector3f((float) entity.getX(), (float) entity.getY(), (float) entity.getZ()));
        float serverYaw = entity.getYRot(tickDelta);
        float serverPitch = entity.getXRot(tickDelta);
        // Initialize on first frame
        if (!this.rotationInitialized) {
            this.smoothedHeadYaw = serverYaw;
            this.smoothedHeadPitch = serverPitch;
            this.simulatedBodyYaw = serverYaw;
            this.lastSignificantHeadYaw = serverYaw;
            this.headStableStartMs = System.currentTimeMillis();
            this.rotationInitialized = true;
        }

        // --- Head smoothing: fast interpolation towards server values ---
        this.smoothedHeadYaw += Mth.wrapDegrees(serverYaw - this.smoothedHeadYaw) * 0.5F;
        this.smoothedHeadPitch += (serverPitch - this.smoothedHeadPitch) * 0.5F;

        // --- Body delay mechanism (based on vanilla BodyControl) ---
        // Max allowed head-body angle (vanilla: getMaxHeadRotation() = 75 for most mobs)
        float maxHeadRot = 50.0F;
        long now = System.currentTimeMillis();

        if (Math.abs(Mth.wrapDegrees(this.smoothedHeadYaw - this.lastSignificantHeadYaw)) > 15.0F) {
            // Head moved significantly: restart delay, clamp body within range
            this.lastSignificantHeadYaw = this.smoothedHeadYaw;
            this.headStableStartMs = now;
            this.simulatedBodyYaw = Mth.rotateIfNecessary(this.simulatedBodyYaw, this.smoothedHeadYaw, maxHeadRot);
        } else {
            long elapsedMs = now - this.headStableStartMs;
            if (elapsedMs > 500) {
                // After 0.5s delay: gradually reduce allowed body-head angle over 0.5s
                float progress = Mth.clamp((elapsedMs - 500) / 500.0F, 0.0F, 1.0F);
                float maxAngle = maxHeadRot * (1.0F - progress);
                this.simulatedBodyYaw = Mth.rotateIfNecessary(this.simulatedBodyYaw, this.smoothedHeadYaw, maxAngle);
            }
        }

        state.yaw = this.smoothedHeadYaw;
        state.bodyYaw = this.simulatedBodyYaw;
        state.bodyPitch = this.smoothedHeadPitch;

        // target_x_rotation = head pitch (body pitch ≈ 0 for standing entities)
        // target_y_rotation = head yaw - body yaw (from body delay mechanism)
        state.setTargetXRotation(state.bodyPitch);
        state.setTargetYRotation(Mth.wrapDegrees(state.yaw - state.bodyYaw));

        // Per-frame entity state for MoLang queries
        state.setEntityOnGround(entity.onGround());
        state.setEntityAlive(entity.isAlive());
        state.setEntityLifeTime(entity.tickCount / 20.0f);

        // LimbAnimator-style movement tracking.
        // getPosition(float) returns interpolated positions using previous/current entity position.
        final Vec3 pos = entity.getPosition(tickDelta);

        if (this.lastAge < 0) {
            this.lastPosX = pos.x;
            this.lastPosY = pos.y;
            this.lastPosZ = pos.z;
            this.lastAge = entity.tickCount;
        }

        final double mx = pos.x - this.lastPosX;
        final double my = pos.y - this.lastPosY;
        final double mz = pos.z - this.lastPosZ;
        this.tickHorizDist += (float) Math.sqrt(mx * mx + mz * mz);
        this.tickVertDist += (float) my;

        this.lastPosX = pos.x;
        this.lastPosY = pos.y;
        this.lastPosZ = pos.z;

        // Per-tick: LimbAnimator 0.4 exponential smoothing
        if (entity.tickCount != this.lastAge) {
            this.limbSpeed += (this.tickHorizDist - this.limbSpeed) * 0.4f;
            this.limbDistance += this.limbSpeed;
            this.limbVerticalSpeed += (this.tickVertDist - this.limbVerticalSpeed) * 0.4f;

            this.tickHorizDist = 0;
            this.tickVertDist = 0;
            this.lastAge = entity.tickCount;
        }

        state.distanceTraveled = this.limbDistance;
        state.setPositionDeltaX(mx);
        state.setPositionDeltaY(my);
        state.setPositionDeltaZ(mz);
        state.setGroundSpeed(this.limbSpeed * 20.0f);
        state.setVerticalSpeed(this.limbVerticalSpeed * 20.0f);

        // Calculate rotation_to_camera for billboard effect
        final var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        final Vec3 cameraPos = camera.getPosition();
        final Vec3 camEntityPos = entity.getPosition(tickDelta);
        final double cdx = cameraPos.x - camEntityPos.x;
        final double cdy = cameraPos.y - camEntityPos.y;
        final double cdz = cameraPos.z - camEntityPos.z;
        final double cameraHorizontalDist = Math.sqrt(cdx * cdx + cdz * cdz);
        state.setRotationToCameraX(-(float) Math.toDegrees(Math.atan2(cdy, cameraHorizontalDist)));
        state.setRotationToCameraY(-(float) (Math.toDegrees(Math.atan2(cdx, cdz)) + state.bodyYaw - 180.0));

        state.setDistanceFromCamera(Math.sqrt(cdx * cdx + cdy * cdy + cdz * cdz));
    }

    private void setupTransforms(CustomEntityRenderState state, PoseStack matrices) {
        matrices.mulPose(Axis.YP.rotationDegrees(180 - state.bodyYaw));
    }

    /**
     * Builds a per-frame scope with complete query bindings (entity-level + per-frame).
     * Uses LayeredScope on top of entityScope to avoid expensive deep copy.
     */
    private Scope buildFrameScope(CustomEntityRenderState state) {
        reusableFrameScope.reset(this.ticker.getEntityScope());

        // Reuse the instance field instead of allocating a fresh MutableObjectBinding (with its backing
        // CaseInsensitiveStringHashMap) every frame. Entity-level keys are updated incrementally.
        final MutableObjectBinding queryBinding = this.reusableQueryBinding;

        // Entity-level queries (variant, flags) from ticker
        this.ticker.populateEntityQueries(queryBinding, this.reusableEntityQueryState);

        // Per-frame movement and state queries
        queryBinding.set("modified_distance_moved", Value.of(state.getDistanceTraveled()));
        queryBinding.set("modified_move_speed", Value.of(state.getGroundSpeed()));
        queryBinding.set("is_on_ground", state.isEntityOnGround() ? VAL_TRUE : VAL_FALSE);
        queryBinding.set("is_alive", state.isEntityAlive() ? VAL_TRUE : VAL_FALSE);
        queryBinding.set("life_time", Value.of(state.getEntityLifeTime()));
        queryBinding.set("ground_speed", Value.of(state.getGroundSpeed()));
        queryBinding.set("vertical_speed", Value.of(state.getVerticalSpeed()));
        queryBinding.set("distance_from_camera", Value.of(state.getDistanceFromCamera()));

        // Rotation queries
        queryBinding.set("body_y_rotation", Value.of(state.getBodyYaw()));
        queryBinding.set("body_x_rotation", Value.of(state.getBodyPitch()));
        queryBinding.set("target_x_rotation", Value.of(state.getTargetXRotation()));
        queryBinding.set("target_y_rotation", Value.of(state.getTargetYRotation()));
        queryBinding.set("head_x_rotation", Value.of(state.getTargetXRotation()));
        queryBinding.set("head_y_rotation", Value.of(state.getTargetYRotation()));

        // Function-type queries
        queryBinding.setFunction("position_delta", (double arg) -> {
            int axis = (int) arg;
            if (axis == 0) return state.getPositionDeltaX();
            if (axis == 1) return state.getPositionDeltaY();
            if (axis == 2) return state.getPositionDeltaZ();
            return 0.0;
        });
        queryBinding.setFunction("rotation_to_camera", (double arg) -> {
            if ((int) arg == 0) return (double) state.getRotationToCameraX();
            if ((int) arg == 1) return (double) state.getRotationToCameraY();
            return 0.0;
        });

        reusableFrameScope.set("query", queryBinding);
        reusableFrameScope.set("q", queryBinding);

        return reusableFrameScope;
    }

    /**
     * Evaluates animation blend weight conditions using pre-parsed expressions.
     */
    private void evaluateAnimationConditions(final Scope frameScope) {
        this.animators.forEach((animId, animator) -> {
            final List<Expression> parsedCondition = this.ticker.getParsedAnimationConditions().get(animId);
            if (parsedCondition != null) {
                try {
                    float weight = (float) MoLangEngine.eval(frameScope, parsedCondition).getAsNumber();
                    animator.setBlendWeight(weight);
                } catch (Throwable e) {
                    animator.setBlendWeight(0);
                }
            } else {
                animator.setBlendWeight(1.0f);
            }
        });
    }

    @Override
    public CustomEntityRenderState createRenderState() {
        return new CustomEntityRenderState();
    }

    /**
     * @param parsedPartVisibility Pre-parsed part visibility map. Values are Boolean (for "true"/"false" literals)
     *                             or {@code List<Expression>} (for MoLang expressions). Null if no visibility rules.
     */
    public record Model(String key, String geometry, CustomEntityModel<CustomEntityRenderState> model, ResourceLocation texture, Material material, BedrockRenderController controller,
                        net.easecation.bedrockmotion.pack.definitions.VisibleBounds visibleBounds,
                        Map<String, Object> parsedPartVisibility) {
    }

    @Getter
    public static class CustomEntityRenderState extends EntityRenderState {
        private float yaw, bodyYaw, bodyPitch;
        private float distanceTraveled;
        @Setter
        private CustomEntityRenderer<?> customRenderer;
        @Setter
        private float rotationToCameraX;
        @Setter
        private float rotationToCameraY;
        @Setter
        private float targetXRotation;
        @Setter
        private float targetYRotation;
        // Per-frame entity state for MoLang queries
        @Setter private boolean entityOnGround;
        @Setter private boolean entityAlive;
        @Setter private float entityLifeTime;
        @Setter private double positionDeltaX;
        @Setter private double positionDeltaY;
        @Setter private double positionDeltaZ;
        @Setter private float groundSpeed;
        @Setter private float verticalSpeed;
        @Setter private double distanceFromCamera;
    }

    /**
     * @param parsedPV Pre-parsed part visibility map. Values are Boolean or {@code List<Expression>}.
     */
    @SuppressWarnings("unchecked")
    private void applyPartVisibility(CustomEntityModel<?> entityModel, Map<String, Object> parsedPV, Scope scope) {
        // Resolve the rule → ModelPart[] mapping once per model (cached), then iterate arrays per frame
        // — no parsedPV.entrySet() iteration, no partsByName.get() (HashMap get + String hashCode), and
        // no per-frame entityModel.allParts() (which builds a fresh List via Stream.collect each call).
        final ResolvedPartVisibility rv = partVisibilityCache.computeIfAbsent(entityModel, m -> resolvePartVisibility(m, parsedPV));

        final boolean defaultVisible = rv.defaultRule != null ? evalParsedVisibility(rv.defaultRule, scope) : true;

        // Set default visibility for all parts (snapshot array — no per-frame Stream/List allocation)
        final ModelPart[] all = rv.allParts;
        for (int i = 0; i < all.length; i++) {
            all[i].visible = defaultVisible;
        }

        // Override specific bones via pre-resolved target arrays.
        boolean anyHidden = !defaultVisible;
        final Object[] rules = rv.rules;
        final ModelPart[][] targets = rv.targets;
        for (int i = 0; i < rules.length; i++) {
            final boolean vis = evalParsedVisibility(rules[i], scope);
            if (!vis) anyHidden = true;
            final ModelPart[] t = targets[i];
            for (int j = 0; j < t.length; j++) {
                t[j].visible = vis;
            }
        }

        // Post-pass: only needed when some parts are hidden.
        if (anyHidden) {
            ensureAncestorsVisible(entityModel.root());
        }
    }

    /**
     * Builds a {@link ResolvedPartVisibility} for a model: snapshots {@code allParts()} into an array
     * (avoiding the per-frame Stream.collect allocation) and pre-resolves each non-wildcard rule's bone
     * name to its {@code ModelPart[]} via {@code getPartsByName()}. Called once per model, cached in
     * {@link #partVisibilityCache}.
     */
    private static ResolvedPartVisibility resolvePartVisibility(final CustomEntityModel<?> entityModel, final Map<String, Object> parsedPV) {
        final ModelPart[] allParts = entityModel.allParts().toArray(EMPTY_MODEL_PARTS);
        final Object defaultRule = parsedPV.get("*");

        // Count non-wildcard rules (the map may or may not contain a "*" entry).
        int count = parsedPV.size();
        if (defaultRule != null && parsedPV.containsKey("*")) {
            count--;
        }
        final Object[] rules = new Object[count];
        final ModelPart[][] targets = new ModelPart[count][];

        final Map<String, List<ModelPart>> partsByName = entityModel.getPartsByName();
        int i = 0;
        for (Map.Entry<String, Object> entry : parsedPV.entrySet()) {
            if ("*".equals(entry.getKey())) continue;
            rules[i] = entry.getValue();
            final List<ModelPart> matching = partsByName.get(entry.getKey());
            targets[i] = (matching != null && !matching.isEmpty()) ? matching.toArray(EMPTY_MODEL_PARTS) : EMPTY_MODEL_PARTS;
            i++;
        }
        return new ResolvedPartVisibility(allParts, defaultRule, rules, targets);
    }

    /**
     * Per-model resolved part_visibility rules. Built once, reused every frame to avoid HashMap lookups
     * and per-frame List allocation in {@link #applyPartVisibility}.
     */
    private static final class ResolvedPartVisibility {
        final ModelPart[] allParts;     // snapshot of entityModel.allParts()
        final Object defaultRule;       // "*" rule value, or null if no wildcard rule
        final Object[] rules;           // non-wildcard rule values (Boolean or List<Expression>)
        final ModelPart[][] targets;    // targets[i] = parts matching rules[i], pre-resolved

        ResolvedPartVisibility(ModelPart[] allParts, Object defaultRule, Object[] rules, ModelPart[][] targets) {
            this.allParts = allParts;
            this.defaultRule = defaultRule;
            this.rules = rules;
            this.targets = targets;
        }
    }

    /**
     * Bottom-up recursive pass: if any descendant has visible=true,
     * this part must also be visible=true to allow rendering traversal
     * to reach the visible descendant.
     */
    private boolean ensureAncestorsVisible(ModelPart part) {
        return ensureAncestorsVisibleImpl(part, 0);
    }

    private boolean ensureAncestorsVisibleImpl(ModelPart part, int depth) {
        if (depth > 200) {
            ViaBedrockUtilityNeoForge.LOGGER.error("[VBU] ensureAncestorsVisible depth > 200 — likely cycle! bone='{}'",
                    ((IModelPart) ((Object) part)).viaBedrockUtility$getName());
            return false;
        }
        boolean anyChildVisible = false;
        for (ModelPart child : ((IModelPart) ((Object) part)).viaBedrockUtility$getChildren().values()) {
            if (ensureAncestorsVisibleImpl(child, depth + 1)) {
                anyChildVisible = true;
            }
        }
        if (anyChildVisible) {
            part.visible = true;
        }
        return part.visible;
    }

    /**
     * Dumps ModelPart tree hierarchy to log for debugging cyclic references.
     * Uses IdentityHashSet to detect and report cycles.
     */
    private void dumpModelHierarchy(ModelPart part, String indent, int depth) {
        if (depth > 20) {
            ViaBedrockUtilityNeoForge.LOGGER.error("{}... (truncated at depth 20)", indent);
            return;
        }
        String name = ((IModelPart) ((Object) part)).viaBedrockUtility$getName();
        boolean isVBU = ((IModelPart) ((Object) part)).viaBedrockUtility$isVBUModel();
        Map<String, ModelPart> children = ((IModelPart) ((Object) part)).viaBedrockUtility$getChildren();
        ViaBedrockUtilityNeoForge.LOGGER.error("{}bone='{}' isVBU={} children={} identity={}",
                indent, name, isVBU, children.size(), System.identityHashCode(part));
        for (Map.Entry<String, ModelPart> entry : children.entrySet()) {
            dumpModelHierarchy(entry.getValue(), indent + "  ", depth + 1);
        }
    }

    /**
     * Evaluates a pre-parsed visibility value. Boolean for literal "true"/"false",
     * or {@code List<Expression>} for MoLang expressions (avoids per-frame re-parsing).
     */
    @SuppressWarnings("unchecked")
    private boolean evalParsedVisibility(Object parsedValue, Scope scope) {
        if (parsedValue instanceof Boolean b) return b;
        try {
            return MoLangEngine.eval(scope, (List<Expression>) parsedValue).getAsBoolean();
        } catch (Throwable e) {
            return true;
        }
    }

    public void reset() {
        this.animators.values().forEach(Animator::stop);
        this.animators.clear();
        this.hasRetainedAnimatedPose = false;
        this.invalidateFrozenMeshes("animation_reset");
    }

    public void play(final AnimationDefinitions.AnimationData data) {
        if (data == null) {
            return;
        }

        this.animators.put(data.animation().getIdentifier(), new Animator(this.ticker, data));
    }
}
