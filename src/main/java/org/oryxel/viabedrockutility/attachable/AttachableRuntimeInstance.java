package org.oryxel.viabedrockutility.attachable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.easecation.bedrockmotion.attachable.AttachableAnimationRuntime;
import net.easecation.bedrockmotion.animator.AnimationClock;
import net.easecation.bedrockmotion.model.AnimationEventListener;
import net.easecation.bedrockmotion.model.AnimationParticleEvent;
import net.easecation.bedrockmotion.model.AnimationSoundEvent;
import net.easecation.bedrockmotion.pack.PackManager;
import net.easecation.bedrockmotion.pack.definitions.AttachableDefinitions;
import net.easecation.bedrockmotion.render.RenderControllerEvaluator;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.oryxel.viabedrockutility.adapter.McBoneModel;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;
import org.oryxel.viabedrockutility.mixin.interfaces.ICuboid;
import org.oryxel.viabedrockutility.renderer.BedrockPlayerModelMetadata;
import org.oryxel.viabedrockutility.util.GeometryUtil;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.Value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** One live attachable: animation sampling, two-phase pass submission, binding/material/visibility state. */
final class AttachableRuntimeInstance implements AutoCloseable {
    private final AttachableDefinitions.AttachableDefinition definition;
    private final PackManager packs;
    private final String queryIdentifier;
    private final AnimationClock.Client clock = new AnimationClock.Client();
    private final MutableObjectBinding variables = new MutableObjectBinding();
    private final AttachableScopeFactory.RuntimeActor actor = new AttachableScopeFactory.RuntimeActor(variables);
    private final Scope persistentScope = net.easecation.bedrockmotion.mocha.MoLangEngine.newScope();
    private final AttachableAnimationRuntime animation;
    private final Map<ModelKey, ModelState> models = new HashMap<>();
    private AttachableScopeFactory.RuntimeScope frame;
    private AttachableOwnerSnapshot owner = AttachableOwnerSnapshot.EMPTY;
    private Entity ownerEntity;
    private AttachableItemSnapshot item = AttachableItemSnapshot.EMPTY;
    private AttachableQueryContext.LogicalHand hand;
    private HumanoidArm arm;
    private AttachableQueryContext.ViewContext view;
    volatile String lastBinding = "";
    volatile String lastHostProfile = "";
    volatile List<String> lastSemanticChain = List.of();
    volatile List<String> lastPresentationChain = List.of();
    volatile Map<String, String> lastControllerStates = Map.of();
    volatile List<String> lastRenderPasses = List.of();
    volatile Matrix4f lastPhysicalAnchorMatrix;
    volatile Matrix4f lastGeometryInstallationMatrix;
    private volatile Model lastGeometryModel;
    volatile String lastRenderPath = "VBU_ATTACHABLE";
    volatile String lastFailure = "Runtime did not submit geometry";
    private final List<PendingParticleEvent> pendingParticleEvents = new ArrayList<>();
    private final List<PendingSoundEvent> pendingSoundEvents = new ArrayList<>();
    // Transient effects wait only for a nearby displayed pose, never for a culled holder to return.
    private static final long EVENT_POSE_MAX_AGE_TICKS = 2;
    private static final int MAX_PENDING_EVENTS = 128;
    private long expiredEvents, overflowEvents, consumedEvents;

    private record PendingParticleEvent(String alias, String locator, String preEffectExpression, long tick) {}
    private record PendingSoundEvent(String alias, String locator, long tick) {}
    private record ModelKey(String geometry, String texture, Set<String> entityRoots) {}

    AttachableRuntimeInstance(AttachableDefinitions.AttachableDefinition definition, PackManager packs) {
        this(definition, packs, definition.identifier());
    }

    AttachableRuntimeInstance(AttachableDefinitions.AttachableDefinition definition, PackManager packs, String queryIdentifier) {
        this.definition = definition;
        this.packs = packs;
        this.queryIdentifier = queryIdentifier;
        persistentScope.set("variable", variables);
        persistentScope.set("v", variables);
        this.animation = new AttachableAnimationRuntime(definition.data(), definition.program(), packs, clock,
                new AnimationEventListener() {
                    @Override
                    public void onTimelineEvent(List<String> expressions) {
                        try {
                            for (String expression : expressions) {
                                net.easecation.bedrockmotion.mocha.MoLangEngine.eval(
                                        frame.scope(), frame.context(), expression);
                            }
                        } catch (IOException | RuntimeException failure) {
                            AttachableDebugLog.warnOnce(definition.identifier() + ":timeline-expression",
                                    "[Attachable] Timeline expression failed for '" + definition.identifier() + "'", failure);
                        }
                    }

                    @Override
                    public void onParticleEvent(AnimationParticleEvent event) {
                        if (acceptEvent(event.tick())) pendingParticleEvents.add(new PendingParticleEvent(
                                event.effect(), event.locator(), event.preEffectExpression(), event.tick()));
                    }

                    @Override
                    public void onSoundEvent(AnimationSoundEvent event) {
                        if (acceptEvent(event.tick())) pendingSoundEvents.add(new PendingSoundEvent(
                                event.effect(), event.locator(), event.tick()));
                    }

                    @Override
                    public Scope getEntityScope() {
                        return persistentScope;
                    }
                });
    }

    void update(AttachableOwnerSnapshot owner, Entity ownerEntity, AttachableItemSnapshot item,
                AttachableQueryContext.LogicalHand hand, HumanoidArm arm,
                AttachableQueryContext.ViewContext view, long tick, float partialTick) {
        var attributionProfiler = net.minecraft.util.profiling.Profiler.get();
        attributionProfiler.push("ec:viabedrockutility/attachable/shared_runtime_update");
        try {
        this.owner = owner;
        this.ownerEntity = ownerEntity;
        this.item = item;
        this.hand = hand;
        this.arm = arm;
        this.view = view;
        actor.variables.set("is_enchanted", Value.of(item.stack().hasFoil()));
        frame = AttachableScopeFactory.RuntimeScope.persistent(persistentScope, actor, owner, ownerEntity, item,
                hand, arm, view, tick, partialTick, queryIdentifier);
        } finally { attributionProfiler.pop(); }
    }

    void skinContext(String variantQuery, int variant) {
        variables.set("is_first_person", Value.of(true));
        variables.set("is_paperdoll", Value.of(false));
        variables.set("map_face_icon", Value.of(false));
        var query = (MutableObjectBinding) frame.scope().get("query");
        var inherited = query.get("mod");
        var mod = new net.easecation.bedrockmotion.mocha.OverlayBinding(
                inherited instanceof MutableObjectBinding binding ? binding : null);
        mod.set(variantQuery.substring("query.mod.".length()), Value.of(variant));
        query.set("mod", mod);
    }

    void advanceTo(long tick, AttachableQueryContext.ViewContext view) throws IOException {
        var attributionProfiler = net.minecraft.util.profiling.Profiler.get();
        attributionProfiler.push("ec:viabedrockutility/attachable/shared_animation_tick");
        try {
        if (frame == null || tick == animation.lastTick()) {
            return;
        }
        this.view = view;
        frame = AttachableScopeFactory.RuntimeScope.persistent(persistentScope, actor, owner, ownerEntity, item,
                hand, arm, view, tick, 0.0F, queryIdentifier);
        advanceCurrentFrameTo(tick);

        } finally { attributionProfiler.pop(); }
    }

    /**
     * Advances a runtime created after the client tick before its first visible render. The current
     * frame is retained so item, hand, view and partial-frame queries match the geometry being
     * submitted. AttachableAnimationRuntime rejects duplicate advances for the same tick.
     */
    boolean advanceCurrentFrameTo(long tick) throws IOException {
        if (frame == null) {
            return false;
        }
        expireEvents(tick);
        updatePoseOverride();
        return animation.tick(tick, frame.scope(), frame.context());
    }

    private void updatePoseOverride() {
        animation.setPoseOverride(AttachablePoseLayers.current(ownerEntity, item.itemIdentifier().toString(), hand, view));
    }

    void prepareFrame(long tick, float partialTick) {
        updatePoseOverride();
        animation.prepareFrame(org.oryxel.viabedrockutility.animation.BedrockFrameTime.INSTANCE.frame(),
                view, tick, partialTick, frame.scope(), frame.context());
    }

    boolean hasAnimator(String identifier) {
        return animation.animators().containsKey(identifier);
    }

    /** Explicit diagnostics only: reads existing variables, never evaluates a script or advances time. */
    Map<String, Object> inspectVariables(List<String> names) {
        final Map<String, Double> selected = new java.util.LinkedHashMap<>();
        for (String requested : names) {
            final String name = requested.startsWith("variable.") ? requested.substring(9)
                    : requested.startsWith("v.") ? requested.substring(2) : requested;
            if (variables.getProperty(name) != null) selected.put(requested, variables.get(name).getAsNumber());
        }
        Map<String, Object> result = new LinkedHashMap<>(Map.of("tick", animation.lastTick(), "view", view.name(),
                "display_frame", animation.lastFrame(), "prepared_frames", animation.preparedFrames(),
                "variables", Map.copyOf(selected), "cached_animations", List.copyOf(animation.animators().keySet()),
                "active_animations", animation.activeAnimations(),
                "frame_cache", animation.frameCacheStatistics(),
                "binding", lastBinding, "host_inheritance", models.values().stream()
                        .filter(model -> model.inheritance != null).flatMap(model -> model.inheritance.description().stream()).distinct().toList()));
        result.put("events", Map.of("pending", pendingParticleEvents.size() + pendingSoundEvents.size(),
                "expired", expiredEvents, "overflow", overflowEvents, "consumed", consumedEvents));
        return Map.copyOf(result);
    }

    boolean render(AttachableHostContext host, PoseStack poses,
                   MultiBufferSource buffers, int packedLight, float partialTick,
                   FirstPersonHostMeshRenderer hostMeshRenderer) throws IOException {
        return render(host, poses, buffers, packedLight, partialTick, hostMeshRenderer, null);
    }

    boolean render(AttachableHostContext host, PoseStack poses,
                   MultiBufferSource buffers, int packedLight, float partialTick,
                   FirstPersonHostMeshRenderer hostMeshRenderer, ResourceLocation skin) throws IOException {
        var attributionProfiler = net.minecraft.util.profiling.Profiler.get();
        attributionProfiler.push("ec:viabedrockutility/attachable/draw");
        try {
        final List<RenderControllerEvaluator.EvaluatedRenderPass> passes =
                animation.evaluateRenderPasses(frame.scope(), frame.context());
        lastControllerStates = animation.controllers().entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        entry -> String.valueOf(entry.getValue().currentStateName())));
        lastRenderPasses = passes.stream().map(RenderControllerEvaluator.EvaluatedRenderPass::key).toList();
        if (passes.isEmpty()) {
            lastFailure = "Render controller evaluation produced no passes";
            AttachableDebugLog.warnOnce(definition.identifier() + ":no-render-passes",
                    "[Attachable] Render controller evaluation produced no passes for "
                            + definition.identifier(), null);
            return false;
        }

        lastFailure = "";
        // Phase 1 (preflight): pure validation. Geometry, per-root bindings, host bones and
        // textures are resolved for every pass before anything is committed, so a fatal
        // failure (missing host bone, invalid texture, MoLang binding error) aborts here and
        // the caller's vanilla fallback never composites with a half-submitted attachable.
        final List<AttachableRenderPlanner.PlannedPass<BedrockPlayerModelMetadata.Bone>> planned =
                AttachableRenderPlanner.plan(passes,
                        geometryName -> packs.getModelDefinitions().getEntityModels().get(geometryName),
                        binding -> host.bindingBone(binding, frame.hand(), frame.arm()) != null,
                        AttachableRenderPlanner.ENTITY_SPACE_BINDING,
                        binding -> host.bindingBone(binding, frame.hand(), frame.arm()),
                        frame.scope(), frame.context(),
                        (warnKey, message) -> AttachableDebugLog.warnOnce(definition.identifier() + ":" + warnKey,
                                "[Attachable] " + message, null),
                        detail -> lastFailure = detail);
        if (planned == null) {
            return false;
        }
        if (planned.isEmpty()) {
            lastFailure = "Every render pass referenced a missing geometry";
            AttachableDebugLog.warnOnce(definition.identifier() + ":no-planned-geometry",
                    "[Attachable] No render pass resolved usable geometry for "
                            + definition.identifier() + "; suppressing vanilla item", null);
            return false;
        }
        lastBinding = planned.stream()
                .flatMap(pass -> pass.rootBindings().entrySet().stream())
                .map(entry -> entry.getKey() + "->" + entry.getValue())
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));

        // Phase 2 (commit): preflight passed, so this render owns the frame and never composites
        // with the vanilla item. The first-person host mesh commits exactly once, after preflight;
        // a failing pass is skipped with a warning instead of failing the frame.
        if (frame.view() == AttachableQueryContext.ViewContext.FIRST_PERSON) {
            new FirstPersonRenderCommit(host.firstPersonHostMeshPolicy(), hostMeshRenderer).commit();
        }
        int submittedPasses = 0;
        for (AttachableRenderPlanner.PlannedPass<BedrockPlayerModelMetadata.Bone> plannedPass : planned) {
            try {
                final Set<String> entityRoots = !plannedPass.rootBindings().containsValue(AttachableRenderPlanner.ENTITY_SPACE_BINDING)
                        ? Set.of() : plannedPass.rootBindings().entrySet().stream()
                        .filter(entry -> AttachableRenderPlanner.ENTITY_SPACE_BINDING.equals(entry.getValue()))
                        .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet());
                final ModelKey modelKey = new ModelKey(plannedPass.pass().geometryValue(),
                        plannedPass.pass().textureValue(), entityRoots);
                final ModelState model = models.computeIfAbsent(modelKey,
                        ignored -> {
                            final Model built = GeometryUtil.buildAttachableModel(
                                    plannedPass.geometry(), plannedPass.pass().geometryValue(),
                                    alias -> AttachableTextureResolver.resolve(
                                            packs, definition.data().getTextures(), alias,
                                            plannedPass.pass().textureValue()), entityRoots);
                            return new ModelState(built, new McBoneModel(built), GeometryUtil.attachableAnchors(plannedPass.geometry(), entityRoots));
                        });
                lastGeometryModel = model.model();
                animation.sample(model.bones(), partialTick, frame.scope(), frame.context(),
                        model.inheritance(plannedPass.geometry(), host));
                boolean submittedPass = false;
                for (AttachableRenderPlanner.HostGroup<BedrockPlayerModelMetadata.Bone> group
                        : plannedPass.hostGroups().values()) {
                    poses.pushPose();
                    try {
                        submittedPass |= renderGroup(host, model, plannedPass, group,
                                poses, buffers, packedLight, skin);
                    } finally {
                        poses.popPose();
                    }
                }
                if (submittedPass) {
                    submittedPasses++;
                    lastFailure = "";
                }
            } catch (Throwable throwable) {
                AttachableDebugLog.warnOnce(definition.identifier() + ":pass:" + plannedPass.pass().key(),
                        "[Attachable] Render pass '" + plannedPass.pass().key() + "' failed for "
                                + definition.identifier() + "; skipping it", throwable);
            }
        }
        if (submittedPasses == 0) {
            lastFailure = "All attachable render passes failed during model submission";
            AttachableDebugLog.warnOnce(definition.identifier() + ":no-submitted-geometry",
                    "[Attachable] No geometry was submitted for " + definition.identifier()
                            + "; suppressing vanilla item", null);
            return false;
        }
        return true;

        } finally { attributionProfiler.pop(); }
    }

    private boolean renderGroup(AttachableHostContext host, ModelState model,
                                AttachableRenderPlanner.PlannedPass<BedrockPlayerModelMetadata.Bone> plannedPass,
                                AttachableRenderPlanner.HostGroup<BedrockPlayerModelMetadata.Bone> group,
                                PoseStack poses, MultiBufferSource buffers, int packedLight, ResourceLocation skin) {
        final boolean firstPerson = frame.view() == AttachableQueryContext.ViewContext.FIRST_PERSON;
        final BedrockPlayerModelMetadata.Bone hostBone = group.hostBone();
        final Matrix4f physicalAnchor = hostBone == null
                ? (firstPerson ? AttachableHostContext.firstPersonCameraMatrix() : new Matrix4f())
                : firstPerson ? host.firstPersonAttachmentMatrix(hostBone) : host.attachmentMatrix(hostBone);
        final AttachableAnimationRuntime.Scale scale = animation.rootScale();
        final Matrix4f geometryInstallation = BedrockTransformConvention.geometryInstallation(
                physicalAnchor, scale.x(), scale.y(), scale.z());
        lastHostProfile = firstPerson
                ? AttachableHostContext.FIRST_PERSON_PROFILE
                : "third_person_bone_deformation";
        lastSemanticChain = hostBone == null ? List.of("entity") : host.semanticChain(hostBone);
        lastPresentationChain = hostBone == null ? List.of("entity") : host.presentationChain(hostBone);
        lastPhysicalAnchorMatrix = new Matrix4f(physicalAnchor);
        lastGeometryInstallationMatrix = new Matrix4f(geometryInstallation);
        try (var entitySpace = org.oryxel.viabedrockutility.renderer.BedrockEntityRenderSpace.enter(
                poses, firstPerson ? AttachableHostContext.firstPersonCameraMatrix() : null)) {
        poses.mulPose(geometryInstallation);
        if (firstPerson) {
            FirstPersonRenderTrace.record("item_submit", arm, poses);
        }
        // A single host group covers the whole geometry; with per-root bindings each group
        // renders only its own roots' subtrees under its own host bone.
        final List<ModelPart> roots = plannedPass.hostGroups().size() == 1
                ? null : rootParts(model.model(), group.roots());
        if (roots != null && roots.isEmpty()) {
            return false;
        }
        final FlagSnapshot visibility =
                    applyVisibility(model, plannedPass.pass().partVisibility());
        try (var cameraSpace = FirstPersonViewSpace.geometry(plannedPass.pass().geometryValue(), owner.uuid(), hand, firstPerson);
             var worldAnchors = AttachableWorldPoses.geometry(owner.uuid(), item.itemIdentifier().toString(), hand, view, model.anchors)) {
            renderMaterials(model, plannedPass.pass(), skin == null ? plannedPass.texture() : skin,
                    poses, buffers, packedLight, roots, skin == null
                            ? AttachableSurfaces.sample(owner.uuid(), item.itemIdentifier().toString(), hand)
                            : AttachableSurfaces.Uv.IDENTITY);
            worldAnchors.commit();
        } finally {
            if (visibility != null) visibility.close();
        }
        // A failed material submission must not consume effects against a pose that never drew.
        // The bounded queue may be consumed by a later successful pass, or expire on tick.
        dispatchPendingParticleEvents(physicalAnchor);
        return true;
        }
    }

    private void dispatchPendingParticleEvents(Matrix4f physicalAnchor) {
        expireEvents(animation.lastTick());
        if (pendingParticleEvents.isEmpty() && pendingSoundEvents.isEmpty()) return;
        // Consume before invoking external sinks: failures/reentrant passes cannot replay a batch.
        final var particles = List.copyOf(pendingParticleEvents);
        final var sounds = List.copyOf(pendingSoundEvents);
        pendingParticleEvents.clear(); pendingSoundEvents.clear();
        final float x = physicalAnchor.m30();
        final float y = physicalAnchor.m31();
        final float z = physicalAnchor.m32();
        for (PendingParticleEvent event : particles) {
            consumedEvents++;
            try {
                final String identifier = definition.data().getParticleEffects().get(event.alias());
                if (identifier == null || identifier.isBlank()) {
                    AttachableDebugLog.warnOnce(definition.identifier() + ":particle-alias:" + event.alias,
                            "[Attachable] Particle alias '" + event.alias + "' has no definition", null);
                    continue;
                }
                // The physical anchor is the attachable item anchor. A locator-specific offset is only
                // applied when the geometry exposes it. The locator resolver is intentionally a
                // separate pose-provider boundary; never silently substitute the item bone because
                // that would produce a plausible but incorrect muzzle/effect position.
                if (event.locator() != null && !event.locator().isBlank()) {
                    AttachableDebugLog.warnOnce(definition.identifier() + ":locator:" + event.locator(),
                            "[Attachable] Locator '" + event.locator() + "' cannot be resolved by the current pose provider; dropping event", null);
                    continue;
                }
                ViaBedrockUtility.getInstance().spawnParticle(
                        org.oryxel.viabedrockutility.particle.BedrockParticleRequest.builder(identifier)
                                .position(x, y, z)
                                .preEffectExpression(event.preEffectExpression())
                                .source("attachable-animation:" + definition.identifier())
                                .build());
            } catch (RuntimeException failure) {
                AttachableDebugLog.warnOnce(definition.identifier() + ":particle-dispatch",
                        "[Attachable] Particle dispatch failed for '" + definition.identifier() + "'", failure);
            }
        }
        for (PendingSoundEvent event : sounds) {
            consumedEvents++;
            try {
                final String identifier = definition.data().getSoundEffects().get(event.alias());
                if (identifier == null || identifier.isBlank()) {
                    AttachableDebugLog.warnOnce(definition.identifier() + ":sound-alias:" + event.alias(),
                            "[Attachable] Sound alias '" + event.alias() + "' has no definition", null);
                    continue;
                }
                ViaBedrockUtility.getInstance().playParticleSound(identifier, x, y, z, event.locator(), Map.of());
            } catch (RuntimeException failure) {
                AttachableDebugLog.warnOnce(definition.identifier() + ":sound-dispatch",
                        "[Attachable] Sound dispatch failed for '" + definition.identifier() + "'", failure);
            }
        }
    }
    private boolean acceptEvent(long tick) {
        expireEvents(tick);
        if (pendingParticleEvents.size() + pendingSoundEvents.size() < MAX_PENDING_EVENTS) return true;
        overflowEvents++;
        AttachableDebugLog.warnOnce(definition.identifier() + ":event-overflow",
                "[Attachable] Transient event queue is full for '" + definition.identifier() + "'; dropping overflow", null);
        return false;
    }

    private void expireEvents(long tick) {
        int before = pendingParticleEvents.size() + pendingSoundEvents.size();
        if (before == 0) return;
        pendingParticleEvents.removeIf(event -> staleEvent(event.tick(), tick));
        pendingSoundEvents.removeIf(event -> staleEvent(event.tick(), tick));
        expiredEvents += before - pendingParticleEvents.size() - pendingSoundEvents.size();
    }

    private static boolean staleEvent(long created, long now) {
        return created > now || now - created > EVENT_POSE_MAX_AGE_TICKS;
    }

    private List<ModelPart> rootParts(Model model, List<String> rootNames) {
        final List<ModelPart> roots = new ArrayList<>(rootNames.size());
        for (String rootName : rootNames) {
            ModelPart found = null;
            for (ModelPart part : model.allParts()) {
                final IModelPart extension = (IModelPart) (Object) part;
                if (!extension.viaBedrockUtility$isCubeGroup()
                        && rootName.equalsIgnoreCase(extension.viaBedrockUtility$getName())) {
                    found = part;
                    break;
                }
            }
            if (found == null) {
                AttachableDebugLog.warnOnce(definition.identifier() + ":root-part:" + rootName,
                        "[Attachable] Geometry root bone '" + rootName
                                + "' has no model part; skipping it", null);
            } else {
                roots.add(found);
            }
        }
        return roots;
    }

    private void renderMaterials(ModelState state,
                                 RenderControllerEvaluator.EvaluatedRenderPass pass,
                                 ResourceLocation texture, PoseStack poses,
                                 MultiBufferSource buffers, int packedLight, List<ModelPart> roots, AttachableSurfaces.Uv uv) {
        final Model model = state.model();
        if (pass.perBoneMaterial().isEmpty()) {
            renderModel(state, roots, pass, texture, poses, buffers, packedLight,
                    AttachableRenderTypes.renderType(pass, texture), pass.emissive(), pass.glint(), uv);
            return;
        }

        final LinkedHashSet<String> materials = new LinkedHashSet<>(pass.perBoneMaterial().values());
        for (String material : materials) {
            final FlagSnapshot skipped =
                    applyMaterialSelection(state, pass.perBoneMaterial(), material);
            try {
                final String normalized = material.toLowerCase(Locale.ROOT);
                renderModel(state, roots, pass, texture, poses, buffers, packedLight,
                        AttachableRenderTypes.renderType(material, pass, texture),
                        normalized.contains("emissive") && !AttachableMaterials.emissiveAlpha(material),
                        normalized.contains("enchanted") || normalized.contains("glint"), uv);
            } finally {
                skipped.close();
            }
        }
    }

    private void renderModel(ModelState state, List<ModelPart> roots,
                                    RenderControllerEvaluator.EvaluatedRenderPass pass,
                                    ResourceLocation texture, PoseStack poses,
                                    MultiBufferSource buffers, int packedLight,
                                    RenderType type, boolean emissive, boolean glint, AttachableSurfaces.Uv uv) {        final int light = pass.ignoreLighting() || emissive
                ? LightTexture.FULL_BRIGHT : packedLight;
        // Known limitation (mirrors BedrockMotion's EvaluatedRenderPass javadoc): enchanted/glint
        // materials degrade to the vanilla foil buffer, and Bedrock's second texture slot is not rendered.
        final VertexConsumer consumer = AttachableSurfaces.transform(
                ItemRenderer.getFoilBuffer(buffers, type, false, glint), uv);
        final Model model = state.model();
        if (view == AttachableQueryContext.ViewContext.THIRD_PERSON && !glint) {
            var rigid = state.rigid();
            if (rigid != null && rigid.render(roots, poses, consumer, type, glint, light,
                    OverlayTexture.NO_OVERLAY, pass.colorArgb(), uv)) return;
        }
        if (roots == null) {
            model.renderToBuffer(poses, consumer, light, OverlayTexture.NO_OVERLAY, pass.colorArgb());
            return;
        }
        for (ModelPart root : roots) {
            root.render(poses, consumer, light, OverlayTexture.NO_OVERLAY, pass.colorArgb());
        }
    }

    private static FlagSnapshot applyMaterialSelection(
            ModelState model, Map<String, String> rules, String selectedMaterial) {
        final var matches = model.materialSelectors.resolve(rules);
        final FlagSnapshot previous = model.materialFlags.acquire();
        try {
            for (var match : matches) {
                final ModelPart part = match.part();
                String material = null;
                for (String key : match.keys()) material = rules.get(key);
                previous.capture(part);
                part.skipDraw = !selectedMaterial.equals(material);
            }
            return previous;
        } catch (RuntimeException | Error failure) {
            previous.close();
            throw failure;
        }
    }

    private static FlagSnapshot applyVisibility(
            ModelState model, Map<String, Boolean> rules) {
        if (rules.isEmpty()) return null;
        final var matches = model.visibilitySelectors.resolve(rules);
        final FlagSnapshot previous = model.visibilityFlags.acquire();
        try {
            for (var match : matches) {
                final ModelPart part = match.part();
                if (!match.keys().isEmpty()) {
                    previous.capture(part);
                    part.visible = rules.get(match.keys().getLast());
                }
            }
            return previous;
        } catch (RuntimeException | Error failure) {
            previous.close();
            throw failure;
        }
    }

    /** Per-model storage, reused across passes; nested draws retain separate restore frames. */
    private static final class FlagSnapshots {
        private final int capacity;
        private final boolean visibility;
        private final List<FlagSnapshot> frames = new ArrayList<>();
        private int depth;
        FlagSnapshots(int capacity, boolean visibility) { this.capacity = capacity; this.visibility = visibility; }
        FlagSnapshot acquire() {
            if (depth == frames.size()) frames.add(new FlagSnapshot(this, depth, capacity));
            FlagSnapshot frame = frames.get(depth++);
            frame.open = true;
            return frame;
        }
    }

    private static final class FlagSnapshot implements AutoCloseable {
        private final FlagSnapshots owner;
        private final int index;
        private final ModelPart[] parts;
        private final boolean[] values;
        private int count;
        private boolean open;
        FlagSnapshot(FlagSnapshots owner, int index, int capacity) {
            this.owner = owner; this.index = index;
            parts = new ModelPart[capacity]; values = new boolean[capacity];
        }
        void capture(ModelPart part) {
            parts[count] = part;
            values[count++] = owner.visibility ? part.visible : part.skipDraw;
        }
        @Override public void close() {
            if (!open) return;
            if (owner.depth != index + 1) throw new IllegalStateException("Part flags must restore in draw order");
            while (count > 0) {
                int i = --count;
                if (owner.visibility) parts[i].visible = values[i];
                else parts[i].skipDraw = values[i];
                parts[i] = null;
            }
            owner.depth--;
            open = false;
        }
    }

    private static final class ModelState {
        private final Model model;
        private final McBoneModel bones;
        private final Map<AttachableWorldPoses.Target, AttachableWorldPoses.Local> anchors;
        private final AttachablePartSelectors materialSelectors, visibilitySelectors;
        private final FlagSnapshots materialFlags, visibilityFlags;
        private BedrockPlayerModelMetadata inheritedFrom;
        private HostBoneInheritance inheritance;
        private org.oryxel.viabedrockutility.renderer.RigidMeshRenderer rigid;
        private boolean rigidUnavailable;
        org.oryxel.viabedrockutility.renderer.RigidMeshRenderer rigid() {
            if (rigid == null && !rigidUnavailable) {
                try { rigid = new org.oryxel.viabedrockutility.renderer.RigidMeshRenderer(model); }
                catch (RuntimeException failure) {
                    rigidUnavailable = true;
                    org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge.LOGGER.warn("[Attachable] Rigid geometry is unavailable; retaining native rendering", failure);
                }
            }
            return rigid;
        }
        void close() { if (rigid != null) rigid.close(); if (inheritance != null) inheritance.release(); }
        ModelState(Model model, McBoneModel bones, Map<AttachableWorldPoses.Target, AttachableWorldPoses.Local> anchors) {
            this.model = model; this.bones = bones; this.anchors = anchors;
            materialSelectors = new AttachablePartSelectors(model, false);
            visibilitySelectors = new AttachablePartSelectors(model, true);
            materialFlags = new FlagSnapshots(model.allParts().size(), false);
            visibilityFlags = new FlagSnapshots(model.allParts().size(), true);
        }
        Model model() { return model; }
        McBoneModel bones() { return bones; }
        Runnable inheritance(org.cube.converter.model.impl.bedrock.BedrockGeometryModel geometry,
                             AttachableHostContext host) {
            if (inheritedFrom != host.metadata()) {
                if (inheritance != null) inheritance.release();
                inheritedFrom = host.metadata();
                inheritance = new HostBoneInheritance(geometry, model, inheritedFrom);
            }
            return inheritance;
        }
    }

    @Override public void close() {
        for (ModelState model : models.values()) model.close();
        models.clear(); pendingParticleEvents.clear(); pendingSoundEvents.clear();
        lastGeometryModel = null;
    }

    String lastGeometrySummary() {
        Model model = lastGeometryModel;
        if (model == null) return "unbuilt";
        int cuboids = 0;
        int vertices = 0;
        for (ModelPart part : model.allParts()) {
            final IModelPart extension = (IModelPart) (Object) part;
            for (ModelPart.Cube cube : extension.viaBedrockUtility$getCuboids()) {
                cuboids++;
                final ICuboid cuboid = (ICuboid) (Object) cube;
                if (cuboid.viaBedrockUtility$getCompiledGeometry() != null) {
                    vertices += cuboid.viaBedrockUtility$getCompiledGeometry().vertexCount();
                }
            }
        }
        return "modelKind=VBU_GEOMETRY,cuboids=" + cuboids + ",vertices=" + vertices;
    }
}
