package org.oryxel.viabedrockutility.attachable;

import com.mojang.blaze3d.vertex.PoseStack;
import net.easecation.bedrockmotion.pack.PackManager;
import net.easecation.bedrockmotion.pack.definitions.AttachableDefinitions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.cube.converter.model.element.Parent;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.attachable.AttachableDebugLog.AttemptStage;
import org.oryxel.viabedrockutility.attachable.AttachableDebugLog.DebugAttempt;
import org.oryxel.viabedrockutility.attachable.AttachableDebugLog.DebugInfo;
import org.oryxel.viabedrockutility.mixin.interfaces.ICustomPlayerRendererHolder;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import org.oryxel.viabedrockutility.renderer.BedrockPlayerModelMetadata;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * VBU owner/hand attachable facade: entry points, ticking, clearing, lifecycle and delegation.
 * Candidate matching lives in {@link AttachableCandidateMatcher}, per-attachable state in
 * {@link AttachableRuntimeInstance}, detached (GROUND/FIXED) rendering in
 * {@link DetachedAttachableRenderer}, MoLang scope binding in {@link AttachableScopeFactory},
 * render-type mapping in {@link AttachableRenderTypes} and warn-once/debug records in
 * {@link AttachableDebugLog}.
 */
public final class AttachableRuntimeManager {
    private static final long RUNTIME_TTL_TICKS = 20L * 30L;
    private static final long DEBUG_ATTEMPT_TTL_TICKS = 20L * 30L;
    private final AttachableRuntimeRegistry<AttachableRuntimeInstance> runtimes = new AttachableRuntimeRegistry<>();
    private final DetachedAttachableRenderer detached = new DetachedAttachableRenderer();
    private final AttachableDebugAttemptStore debugAttempts = new AttachableDebugAttemptStore();
    private final AttachableClientTickCounter tickCounter = new AttachableClientTickCounter();
    private volatile Set<ResourceLocation> candidateItemIdentifiers = Set.of();

    public AttachableRenderResult renderThirdPerson(PlayerRenderState state, AttachableItemSnapshot item,
                                                    AttachableQueryContext.LogicalHand hand, HumanoidArm physicalArm,
                                                    PlayerModel playerModel, PoseStack poses,
                                                    MultiBufferSource buffers, int packedLight) {
        final ICustomPlayerRendererHolder extension = (ICustomPlayerRendererHolder) state;
        final AttachableOwnerSnapshot owner = extension.viaBedrockUtility$getOwnerSnapshot();
        if (owner.uuid() == null) {
            return AttachableRenderResult.NOT_APPLICABLE;
        }
        return render(owner, item, hand, physicalArm, AttachableQueryContext.ViewContext.THIRD_PERSON,
                playerModel, poses, buffers, packedLight,
                state.ageInTicks - (float) Math.floor(state.ageInTicks), null);
    }

    public AttachableRenderResult renderFirstPerson(AttachableOwnerSnapshot owner, AttachableItemSnapshot item,
                                                    AttachableQueryContext.LogicalHand hand, HumanoidArm physicalArm,
                                                    PlayerModel playerModel, PoseStack poses,
                                                    MultiBufferSource buffers, int packedLight, float partialTick,
                                                    FirstPersonHostMeshRenderer hostMeshRenderer) {
        return render(owner, item, hand, physicalArm, AttachableQueryContext.ViewContext.FIRST_PERSON,
                playerModel, poses, buffers, packedLight, partialTick, hostMeshRenderer);
    }

    public void tick() {
        final Minecraft minecraft = Minecraft.getInstance();
        final AttachableClientTickCounter.Snapshot clock = tickCounter.advance(minecraft.level);
        if (clock.levelChanged()) {
            runtimes.clear();
            debugAttempts.clear();
        }
        if (minecraft.level == null) {
            clear();
            return;
        }
        final long tick = clock.tick();
        for (Map.Entry<AttachableRuntimeRegistry.RuntimeKey, AttachableRuntimeRegistry.EntryView<AttachableRuntimeInstance>> entry
                : runtimes.snapshot().entrySet()) {
            try {
                entry.getValue().runtime().advanceTo(tick,
                        authoritativeTickView(minecraft, entry.getKey().ownerUuid()));
            } catch (Throwable throwable) {
                AttachableDebugLog.warnOnce(entry.getValue().identity().attachableIdentifier() + ":tick",
                        "[Attachable] Runtime tick failed for " + entry.getValue().identity().attachableIdentifier(), throwable);
            }
        }
        runtimes.evictOlderThan(tick - RUNTIME_TTL_TICKS);
        debugAttempts.evictOlderThan(tick - DEBUG_ATTEMPT_TTL_TICKS);
    }

    /**
     * The tick frame consumes the authoritative camera view instead of the view stored by the most
     * recent render, so a camera switch applies on the very next tick rather than one render late.
     * The local player follows the vanilla camera option; remote owners are always third person.
     */
    private static AttachableQueryContext.ViewContext authoritativeTickView(Minecraft minecraft, UUID ownerUuid) {
        final LocalPlayer player = minecraft.player;
        if (player != null && ownerUuid != null && ownerUuid.equals(player.getUUID())) {
            return minecraft.options.getCameraType().isFirstPerson()
                    ? AttachableQueryContext.ViewContext.FIRST_PERSON
                    : AttachableQueryContext.ViewContext.THIRD_PERSON;
        }
        return AttachableQueryContext.ViewContext.THIRD_PERSON;
    }

    /**
     * Drops per-owner runtimes, the detached model cache, attempt diagnostics and the process-wide
     * warn-once dedup set (so a new pack generation may warn again). Registered query providers are
     * mod configuration, not runtime state, and deliberately survive.
     */
    public void clear() {
        runtimes.clear();
        detached.clear();
        debugAttempts.clear();
        AttachableDebugLog.clearWarned();
        AttachableQueryProviders.clearDiagnostics();
    }

    public int size() {
        return runtimes.size();
    }

    public List<DebugInfo> debugSnapshot() {
        return runtimes.snapshot().entrySet().stream().map(entry -> {
            final AttachableRuntimeInstance runtime = entry.getValue().runtime();
            return new DebugInfo(entry.getKey(), entry.getValue().identity(), entry.getValue().lastSeenTick(),
                    runtime.lastBinding,
                    runtime.lastHostProfile, runtime.lastSemanticChain, runtime.lastPresentationChain,
                    runtime.lastControllerStates, runtime.lastRenderPasses,
                    runtime.lastPhysicalAnchorMatrix == null
                            ? null : runtime.lastPhysicalAnchorMatrix.get(new float[16]),
                    runtime.lastGeometryInstallationMatrix == null
                            ? null : runtime.lastGeometryInstallationMatrix.get(new float[16]),
                    runtime.lastGeometrySummary);
        }).toList();
    }

    public List<DebugAttempt> debugAttempts() {
        return debugAttempts.snapshot();
    }

    /** Builds the immutable hot-path index once per pack generation. */
    public void onPackManagerChanged(PackManager packs) {
        if (packs == null || packs.getAttachableDefinitions() == null) {
            candidateItemIdentifiers = Set.of();
            return;
        }
        final Set<ResourceLocation> next = new LinkedHashSet<>();
        for (String rawIdentifier : packs.getAttachableDefinitions().getItemCandidates().keySet()) {
            try {
                next.add(ResourceLocation.parse(rawIdentifier));
            } catch (RuntimeException exception) {
                AttachableDebugLog.warnOnce("candidate-item:" + rawIdentifier,
                        "[Attachable] Ignoring invalid item identifier '" + rawIdentifier + "'", exception);
            }
        }
        candidateItemIdentifiers = Set.copyOf(next);
    }

    /** Captures immutable item state only when the current generation indexes that item. */
    public AttachableItemSnapshot snapshotIfCandidate(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return AttachableItemSnapshot.EMPTY;
        }
        final ResourceLocation identifier = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!candidateItemIdentifiers.contains(identifier)) {
            return AttachableItemSnapshot.EMPTY;
        }
        return AttachableItemSnapshot.of(identifier, stack);
    }

    public boolean renderDetached(AttachableItemSnapshot item, ItemDisplayContext displayContext,
                                  PoseStack poses, MultiBufferSource buffers,
                                  int packedLight, int packedOverlay) {
        return detached.render(item, displayContext, poses, buffers, packedLight, packedOverlay);
    }

    private AttachableRenderResult render(AttachableOwnerSnapshot owner, AttachableItemSnapshot item,
                                          AttachableQueryContext.LogicalHand hand, HumanoidArm physicalArm,
                                          AttachableQueryContext.ViewContext view, PlayerModel playerModel,
                                          PoseStack poses, MultiBufferSource buffers, int packedLight, float partialTick,
                                          FirstPersonHostMeshRenderer hostMeshRenderer) {
        if (item == null || item.isEmpty()) {
            return AttachableRenderResult.NOT_APPLICABLE;
        }
        final ViaBedrockUtility.PackGeneration generation = ViaBedrockUtility.getInstance().getPackGeneration();
        final AttachableRuntimeRegistry.RuntimeKey key = new AttachableRuntimeRegistry.RuntimeKey(owner.uuid(), hand);
        final Minecraft minecraft = Minecraft.getInstance();
        final AttachableClientTickCounter.Snapshot clock = tickCounter.synchronize(minecraft.level);
        if (clock.levelChanged()) {
            runtimes.clear();
            debugAttempts.clear();
        }
        final long tick = clock.tick();
        final PackManager packs = generation.manager();
        if (packs == null || packs.getAttachableDefinitions() == null) {
            recordAttempt(key, tick, generation.generation(), item, view, AttemptStage.PACKS_UNAVAILABLE,
                    0, "", List.of(), "", "PackManager or attachable index is unavailable");
            return AttachableRenderResult.NOT_APPLICABLE;
        }

        final Entity ownerEntity = minecraft.level == null || owner.uuid() == null
                ? null : minecraft.level.getPlayerByUUID(owner.uuid());
        final String itemIdentifier = item.itemIdentifier().toString();
        final int candidateCount = packs.getAttachableDefinitions().candidatesFor(itemIdentifier).size();
        final AttachableCandidateMatcher.Candidate candidate =
                AttachableCandidateMatcher.match(packs, owner, ownerEntity, item, hand, physicalArm, view,
                        tick, partialTick);
        if (candidate == null) {
            recordAttempt(key, tick, generation.generation(), item, view,
                    candidateCount == 0 ? AttemptStage.NO_CANDIDATES : AttemptStage.CONDITION_REJECTED,
                    candidateCount, "", List.of(), "",
                    candidateCount == 0 ? "No attachable is indexed for the Java item identifier"
                            : "All indexed attachable item conditions evaluated false or failed");
            return AttachableRenderResult.NOT_APPLICABLE;
        }

        // A pure Bedrock texture_mesh is the resource-pack representation of a 2D item sprite.
        // Java's ItemRenderer already owns the exact item/generated extrusion, hand transforms,
        // use animation and left/right mirroring. Let it render these meshes directly instead of
        // applying a second attachable bone transform, while retaining VBU for real 3D geometry.
        if (view == AttachableQueryContext.ViewContext.FIRST_PERSON
                || view == AttachableQueryContext.ViewContext.THIRD_PERSON) {
            final String detail = textureMeshOnlyDetail(packs, candidate.definition());
            if (detail != null) {
                recordAttempt(key, tick, generation.generation(), item, view,
                        AttemptStage.JAVA_ITEM_FALLBACK, candidateCount,
                        candidate.definition().identifier(), List.of(), "", detail);
                return AttachableRenderResult.NOT_APPLICABLE;
            }
        }

        final BedrockPlayerModelMetadata metadata = BedrockPlayerModelMetadata.get(playerModel);
        if (metadata == null) {
            recordAttempt(key, tick, generation.generation(), item, view, AttemptStage.METADATA_MISSING,
                    candidateCount, candidate.definition().identifier(), List.of(), "",
                    "PlayerModel has no Bedrock metadata; attachable rendering is suppressed");
            AttachableDebugLog.warnOnce(candidate.definition().identifier() + ":metadata",
                    "[Attachable] PlayerModel has no Bedrock metadata for "
                            + candidate.definition().identifier() + "; suppressing vanilla item", null);
            return AttachableRenderResult.SUPPRESSED;
        }

        final AttachableRuntimeRegistry.RuntimeIdentity identity = new AttachableRuntimeRegistry.RuntimeIdentity(
                itemIdentifier, candidate.definition().identifier(), generation.generation());
        final AttachableRuntimeInstance runtime = runtimes.getOrCreate(key, identity, tick,
                () -> new AttachableRuntimeInstance(candidate.definition(), packs));
        runtime.update(owner, ownerEntity, item, hand, physicalArm, view, tick, partialTick);

        final AttachableHostContext host = new AttachableHostContext(metadata);
        try {
            final boolean rendered = runtime.render(host, poses, buffers, packedLight, partialTick, hostMeshRenderer);
            recordAttempt(key, tick, generation.generation(), item, view,
                    rendered ? AttemptStage.RENDERED : AttemptStage.RUNTIME_REJECTED,
                    candidateCount, candidate.definition().identifier(), runtime.lastRenderPasses,
                    runtime.lastBinding, rendered ? "" : runtime.lastFailure);
            return rendered ? AttachableRenderResult.RENDERED : AttachableRenderResult.SUPPRESSED;
        } catch (Throwable throwable) {
            recordAttempt(key, tick, generation.generation(), item, view, AttemptStage.RUNTIME_EXCEPTION,
                    candidateCount, candidate.definition().identifier(), runtime.lastRenderPasses,
                    runtime.lastBinding, throwable.toString());
            AttachableDebugLog.warnOnce(candidate.definition().identifier() + ":runtime",
                    "[Attachable] Runtime failed for " + candidate.definition().identifier(), throwable);
            return AttachableRenderResult.SUPPRESSED;
        }
    }

    private static String textureMeshOnlyDetail(PackManager packs,
                                                AttachableDefinitions.AttachableDefinition definition) {
        if (definition.data().getGeometries().isEmpty()) {
            return null;
        }
        boolean found = false;
        for (String geometryName : definition.data().getGeometries().values()) {
            final BedrockGeometryModel geometry = packs.getModelDefinitions().getEntityModels().get(geometryName);
            if (geometry == null) {
                continue;
            }
            found = true;
            for (Parent bone : geometry.getParents()) {
                if (!bone.getCubes().isEmpty() || bone.getPolyMesh() != null
                        || bone.getTextureMeshes().isEmpty()) {
                    return null;
                }
            }
        }
        return found ? "Pure texture_mesh attachable delegated to Java ItemRenderer" : null;
    }

    private void recordAttempt(AttachableRuntimeRegistry.RuntimeKey key, long tick, long generation,
                               AttachableItemSnapshot item, AttachableQueryContext.ViewContext view,
                               AttemptStage stage, int candidates, String attachableIdentifier,
                               List<String> renderPasses, String bindingBone, String detail) {
        final DebugAttempt attempt = new DebugAttempt(key, generation, tick,
                item.itemIdentifier().toString(), view,
                stage, candidates, attachableIdentifier, renderPasses, bindingBone, detail);
        final DebugAttempt previous = debugAttempts.record(attempt, tick);
        if (!attempt.equals(previous)) {
            ViaBedrockUtilityNeoForge.LOGGER.debug("[Attachable] Attempt {}", attempt);
        }
    }
}
