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

/** One live attachable: animation sampling, two-phase pass submission, binding/material/visibility state. */
final class AttachableRuntimeInstance {
    private final AttachableDefinitions.AttachableDefinition definition;
    private final PackManager packs;
    private final AnimationClock.Client clock = new AnimationClock.Client();
    private final MutableObjectBinding variables = new MutableObjectBinding();
    private final AttachableScopeFactory.RuntimeActor actor = new AttachableScopeFactory.RuntimeActor(variables);
    private final Scope persistentScope = Scope.create();
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
    volatile String lastGeometrySummary = "unbuilt";
    volatile String lastFailure = "Runtime did not submit geometry";
    private final List<PendingParticleEvent> pendingParticleEvents = new ArrayList<>();
    private final List<PendingSoundEvent> pendingSoundEvents = new ArrayList<>();
    private boolean pendingEventsDispatched;

    private record PendingParticleEvent(String alias, String locator, String preEffectExpression) {}
    private record PendingSoundEvent(String alias, String locator) {}
    private record ModelKey(String geometry, String texture) {}

    AttachableRuntimeInstance(AttachableDefinitions.AttachableDefinition definition, PackManager packs) {
        this.definition = definition;
        this.packs = packs;
        persistentScope.set("variable", variables);
        persistentScope.set("v", variables);
        this.animation = new AttachableAnimationRuntime(definition.data(), packs, clock,
                new AnimationEventListener() {
                    @Override
                    public void onTimelineEvent(List<String> expressions) {
                        // Timeline script expressions remain owned by BedrockMotion. They are not
                        // blindly interpreted as Java sound names; pre_animation/query writes are
                        // evaluated there and typed particle keyframes arrive below.
                        AttachableDebugLog.warnOnce(definition.identifier() + ":timeline-expression",
                                "[Attachable] Timeline expressions received for '" + definition.identifier() + "'", null);
                    }

                    @Override
                    public void onParticleEvent(AnimationParticleEvent event) {
                        pendingParticleEvents.add(new PendingParticleEvent(
                                event.effect(), event.locator(), event.preEffectExpression()));
                        pendingEventsDispatched = false;
                    }

                    @Override
                    public void onSoundEvent(AnimationSoundEvent event) {
                        pendingSoundEvents.add(new PendingSoundEvent(event.effect(), event.locator()));
                        pendingEventsDispatched = false;
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
        this.owner = owner;
        this.ownerEntity = ownerEntity;
        this.item = item;
        this.hand = hand;
        this.arm = arm;
        this.view = view;
        this.pendingEventsDispatched = false;
        actor.variables.set("is_enchanted", Value.of(item.stack().hasFoil()));
        frame = AttachableScopeFactory.RuntimeScope.persistent(persistentScope, actor, owner, ownerEntity, item,
                hand, arm, view, tick, partialTick, definition.identifier());
    }

    void advanceTo(long tick, AttachableQueryContext.ViewContext view) throws IOException {
        if (frame == null || tick == animation.lastTick()) {
            return;
        }
        this.view = view;
        frame = AttachableScopeFactory.RuntimeScope.persistent(persistentScope, actor, owner, ownerEntity, item,
                hand, arm, view, tick, 0.0F, definition.identifier());
        animation.tick(tick, frame.scope(), frame.context());
    }

    boolean render(AttachableHostContext host, PoseStack poses,
                   MultiBufferSource buffers, int packedLight, float partialTick,
                   FirstPersonHostMeshRenderer hostMeshRenderer) throws IOException {
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
                        frame.arm() == HumanoidArm.RIGHT ? "rightitem" : "leftitem",
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
                final ModelKey modelKey = new ModelKey(plannedPass.pass().geometryValue(),
                        plannedPass.pass().textureValue());
                final ModelState model = models.computeIfAbsent(modelKey,
                        ignored -> {
                            final Model built = GeometryUtil.buildAttachableModel(
                                    plannedPass.geometry(), plannedPass.pass().geometryValue(),
                                    alias -> AttachableTextureResolver.resolve(
                                            packs, definition.data().getTextures(), alias,
                                            plannedPass.pass().textureValue()));
                            return new ModelState(built, new McBoneModel(built));
                        });
                lastGeometrySummary = geometrySummary(model.model());
                animation.sample(model.bones(), partialTick, frame.scope(), frame.context());
                boolean submittedPass = false;
                for (AttachableRenderPlanner.HostGroup<BedrockPlayerModelMetadata.Bone> group
                        : plannedPass.hostGroups().values()) {
                    poses.pushPose();
                    try {
                        submittedPass |= renderGroup(host, model, plannedPass, group,
                                poses, buffers, packedLight);
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
    }

    private boolean renderGroup(AttachableHostContext host, ModelState model,
                                AttachableRenderPlanner.PlannedPass<BedrockPlayerModelMetadata.Bone> plannedPass,
                                AttachableRenderPlanner.HostGroup<BedrockPlayerModelMetadata.Bone> group,
                                PoseStack poses, MultiBufferSource buffers, int packedLight) {
        final boolean firstPerson = frame.view() == AttachableQueryContext.ViewContext.FIRST_PERSON;
        final BedrockPlayerModelMetadata.Bone hostBone = group.hostBone();
        final Matrix4f physicalAnchor = firstPerson
                ? host.firstPersonAttachmentMatrix(hostBone)
                : host.attachmentMatrix(hostBone);
        final AttachableAnimationRuntime.Scale scale = animation.rootScale();
        final Matrix4f geometryInstallation = BedrockTransformConvention.geometryInstallation(
                physicalAnchor, scale.x(), scale.y(), scale.z());
        lastHostProfile = firstPerson
                ? AttachableHostContext.FIRST_PERSON_PROFILE
                : "third_person_bone_deformation";
        lastSemanticChain = host.semanticChain(hostBone);
        lastPresentationChain = host.presentationChain(hostBone);
        lastPhysicalAnchorMatrix = new Matrix4f(physicalAnchor);
        lastGeometryInstallationMatrix = new Matrix4f(geometryInstallation);
        dispatchPendingParticleEvents(physicalAnchor);
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
        final Map<ModelPart, Boolean> visibility =
                applyVisibility(model.model(), plannedPass.pass().partVisibility());
        try {
            renderMaterials(model.model(), plannedPass.pass(), plannedPass.texture(),
                    poses, buffers, packedLight, roots);
        } finally {
            visibility.forEach((part, visible) -> part.visible = visible);
        }
        return true;
    }

    private void dispatchPendingParticleEvents(Matrix4f physicalAnchor) {
        if (pendingEventsDispatched || (pendingParticleEvents.isEmpty() && pendingSoundEvents.isEmpty())) return;
        pendingEventsDispatched = true;
        final float x = physicalAnchor.m30();
        final float y = physicalAnchor.m31();
        final float z = physicalAnchor.m32();
        for (PendingParticleEvent event : List.copyOf(pendingParticleEvents)) {
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
        }
        pendingParticleEvents.clear();
        for (PendingSoundEvent event : List.copyOf(pendingSoundEvents)) {
            final String identifier = definition.data().getSoundEffects().get(event.alias());
            if (identifier == null || identifier.isBlank()) {
                AttachableDebugLog.warnOnce(definition.identifier() + ":sound-alias:" + event.alias(),
                        "[Attachable] Sound alias '" + event.alias() + "' has no definition", null);
                continue;
            }
            ViaBedrockUtility.getInstance().playParticleSound(identifier, x, y, z, event.locator(), Map.of());
        }
        pendingSoundEvents.clear();
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

    private void renderMaterials(Model model,
                                 RenderControllerEvaluator.EvaluatedRenderPass pass,
                                 ResourceLocation texture, PoseStack poses,
                                 MultiBufferSource buffers, int packedLight, List<ModelPart> roots) {
        if (pass.perBoneMaterial().isEmpty()) {
            renderModel(model, roots, pass, texture, poses, buffers, packedLight,
                    AttachableRenderTypes.renderType(pass, texture), pass.emissive(), pass.glint());
            return;
        }

        final LinkedHashSet<String> materials = new LinkedHashSet<>(pass.perBoneMaterial().values());
        for (String material : materials) {
            final Map<ModelPart, Boolean> skipped =
                    applyMaterialSelection(model, pass.perBoneMaterial(), material);
            try {
                final String normalized = material.toLowerCase(Locale.ROOT);
                renderModel(model, roots, pass, texture, poses, buffers, packedLight,
                        AttachableRenderTypes.renderType(material, pass, texture), normalized.contains("emissive"),
                        normalized.contains("enchanted") || normalized.contains("glint"));
            } finally {
                skipped.forEach((part, skipDraw) -> part.skipDraw = skipDraw);
            }
        }
    }

    private static void renderModel(Model model, List<ModelPart> roots,
                                    RenderControllerEvaluator.EvaluatedRenderPass pass,
                                    ResourceLocation texture, PoseStack poses,
                                    MultiBufferSource buffers, int packedLight,
                                    RenderType type, boolean emissive, boolean glint) {        final int light = pass.ignoreLighting() || emissive
                ? LightTexture.FULL_BRIGHT : packedLight;
        // Known limitation (mirrors BedrockMotion's EvaluatedRenderPass javadoc): enchanted/glint
        // materials degrade to the vanilla foil buffer, and Bedrock's second texture slot is not rendered.
        final VertexConsumer consumer = ItemRenderer.getFoilBuffer(buffers, type, false, glint);
        if (roots == null) {
            model.renderToBuffer(poses, consumer, light, OverlayTexture.NO_OVERLAY, pass.colorArgb());
            return;
        }
        for (ModelPart root : roots) {
            root.render(poses, consumer, light, OverlayTexture.NO_OVERLAY, pass.colorArgb());
        }
    }

    private static Map<ModelPart, Boolean> applyMaterialSelection(
            Model model, Map<String, String> rules, String selectedMaterial) {
        final LinkedHashMap<ModelPart, Boolean> previous = new LinkedHashMap<>();
        for (ModelPart part : model.allParts()) {
            final IModelPart extension = (IModelPart) (Object) part;
            final String name = extension.viaBedrockUtility$getName();
            if (name == null || extension.viaBedrockUtility$isCubeGroup()) {
                continue;
            }
            String material = null;
            for (Map.Entry<String, String> rule : rules.entrySet()) {
                if (globMatches(rule.getKey(), name)) {
                    material = rule.getValue();
                }
            }
            previous.put(part, part.skipDraw);
            part.skipDraw = !selectedMaterial.equals(material);
        }
        return previous;
    }

    private static Map<ModelPart, Boolean> applyVisibility(
            Model model, Map<String, Boolean> rules) {
        final LinkedHashMap<ModelPart, Boolean> previous = new LinkedHashMap<>();
        if (rules.isEmpty()) {
            return previous;
        }
        for (ModelPart part : model.allParts()) {
            final String name = ((IModelPart) (Object) part).viaBedrockUtility$getName();
            if (name == null) {
                continue;
            }
            for (Map.Entry<String, Boolean> rule : rules.entrySet()) {
                if (globMatches(rule.getKey(), name)) {
                    previous.putIfAbsent(part, part.visible);
                    part.visible = rule.getValue();
                }
            }
        }
        return previous;
    }

    private static boolean globMatches(String pattern, String name) {
        if (pattern.equals("*")) {
            return true;
        }
        final String regex = pattern.replace(".", "\\.").replace("*", ".*");
        return name.matches("(?i)" + regex);
    }

    private record ModelState(Model model, McBoneModel bones) {
    }

    private static String geometrySummary(Model model) {
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
        return "cuboids=" + cuboids + ",vertices=" + vertices;
    }
}
