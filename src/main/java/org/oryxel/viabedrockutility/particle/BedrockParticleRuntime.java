package org.oryxel.viabedrockutility.particle;

import net.easecation.beparticle.ParticleManager;
import net.easecation.beparticle.ParticleSpawnRequest;
import net.easecation.beparticle.ParticleSpawnResult;
import net.easecation.beparticle.ParticleVariableSource;
import net.easecation.beparticle.anchor.AnchorSample;
import net.easecation.beparticle.anchor.ParticleAnchor;
import net.easecation.beparticle.anchor.ParticleAnchors;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VBU-owned entry point for spawning a Bedrock particle effect.
 *
 * <p>The particle definition, MoLang evaluation and rendering remain inside BEParticle. This
 * class deliberately contains no item or gun semantics; integrations provide a Bedrock
 * identifier, an explicit bound/world placement and optional emitter variables.</p>
 */
public final class BedrockParticleRuntime {
    private static final int MAX_PENDING_BOUND_EFFECTS = 1024;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();
    private final BedrockPoseProvider poseProvider;
    private final ConcurrentLinkedQueue<PendingBoundEffect> pending = new ConcurrentLinkedQueue<>();

    public BedrockParticleRuntime() {
        this(new MinecraftBedrockPoseProvider());
    }

    BedrockParticleRuntime(BedrockPoseProvider poseProvider) {
        this.poseProvider = java.util.Objects.requireNonNull(poseProvider, "poseProvider");
    }

    /**
     * Spawns one Bedrock emitter when the currently published particle definitions contain the
     * requested identifier.
     *
     * @return {@code true} when an emitter was created, {@code false} when the runtime is not
     * ready, the identifier is invalid, or the definition is absent
     */
    public boolean spawn(String rawIdentifier, float x, float y, float z,
                         Map<String, Float> molangVariables) {
        return spawn(BedrockParticleRequest.builder(rawIdentifier)
                .position(x, y, z).variables(molangVariables).source("vbu-legacy").build());
    }

    public boolean spawn(BedrockParticleRequest request) {
        final ParticleSpawnResult result = spawnResult(request);
        return result == ParticleSpawnResult.SPAWNED || result == ParticleSpawnResult.QUEUED;
    }

    public ParticleSpawnResult spawnResult(BedrockParticleRequest request) {
        final long currentGeneration = org.oryxel.viabedrockutility.ViaBedrockUtility.getInstance()
                .getPackGeneration().generation();
        if (request.resourceGeneration() >= 0L && request.resourceGeneration() != currentGeneration) {
            warnOnce("generation:" + request.resourceGeneration(),
                    "[Particle] Dropping a request from retired resource generation '{}'",
                    Long.toString(request.resourceGeneration()));
            return ParticleSpawnResult.RUNTIME_NOT_READY;
        }
        final String rawIdentifier = request.identifier();
        final String identifier = normalizeIdentifier(rawIdentifier);
        if (identifier == null) {
            warnOnce("invalid:" + String.valueOf(rawIdentifier),
                    "[Particle] Ignoring invalid Bedrock particle identifier '{}'",
                    String.valueOf(rawIdentifier));
            return ParticleSpawnResult.INVALID_IDENTIFIER;
        }

        final ParticleManager manager = ParticleManager.INSTANCE;
        if (manager.getDefinition(identifier) == null) {
            warnOnce("missing:" + identifier,
                    "[Particle] Bedrock definition '{}' is not loaded; keeping the caller's fallback path",
                    identifier);
            return ParticleSpawnResult.DEFINITION_MISSING;
        }

        final Map<String, Float> variables = copyVariables(request.variables());
        ViaBedrockUtilityNeoForge.LOGGER.debug(
                "[Particle:L4] request rawId={} normalizedId={} semantic={} source={} placement={} variables={}",
                rawIdentifier, identifier, request.placement().semantic(), request.source(),
                request.placement(), variables);
        if (request.placement() instanceof BedrockParticlePlacement.BoundEffect bound) {
            final BedrockPoseSnapshot initial = poseProvider.resolve(bound);
            if (!initial.valid()) {
                if (pending.size() >= MAX_PENDING_BOUND_EFFECTS) {
                    warnOnce("pending-limit", "[Particle] Bound-effect pending queue is full; dropping '{}'", identifier);
                    return ParticleSpawnResult.LIMIT_REACHED;
                }
                pending.add(new PendingBoundEffect(request, identifier, currentTick()));
                return ParticleSpawnResult.QUEUED;
            }
            logResolvedPose(request, identifier, bound, initial);
            return spawnResolved(request, identifier, variables);
        }
        return spawnResolved(request, identifier, variables);
    }

    private ParticleSpawnResult spawnResolved(BedrockParticleRequest request, String identifier,
                                               Map<String, Float> variables) {
        try {
            final ParticleAnchor anchor;
            final ParticleVariableSource variableSource;
            if (request.placement() instanceof BedrockParticlePlacement.BoundEffect bound) {
                anchor = () -> toAnchorSample(poseProvider.resolve(bound));
                variableSource = () -> actorVariables(bound);
            } else if (request.placement() instanceof BedrockParticlePlacement.WorldTrajectory world) {
                anchor = ParticleAnchors.staticWorld(world.origin(), world.orientation());
                variableSource = null;
            } else {
                return ParticleSpawnResult.FAILED;
            }
            return ParticleManager.INSTANCE.spawnEmitter(new ParticleSpawnRequest(
                    identifier, anchor, variables == null ? Map.of() : variables,
                    request.source(), 256, request.preEffectExpression(), variableSource
            )) != null ? ParticleSpawnResult.SPAWNED : ParticleSpawnResult.FAILED;
        } catch (RuntimeException exception) {
            warnOnce("failed:" + identifier,
                    "[Particle] Failed to spawn Bedrock emitter '{}'; keeping the caller's fallback path",
                    identifier);
            ViaBedrockUtilityNeoForge.LOGGER.debug("[Particle] Emitter failure for {}", identifier, exception);
            return ParticleSpawnResult.FAILED;
        }
    }

    /** Retries bound requests once after the model pose has had a client tick to publish. */
    public void tick() {
        final long now = currentTick();
        final int count = pending.size();
        for (int i = 0; i < count; i++) {
            final PendingBoundEffect entry = pending.poll();
            if (entry == null) break;
            final BedrockParticlePlacement.BoundEffect bound =
                    (BedrockParticlePlacement.BoundEffect) entry.request().placement();
            if (poseProvider.resolve(bound).valid()) {
                logResolvedPose(entry.request(), entry.identifier(), bound, poseProvider.resolve(bound));
                spawnResolved(entry.request(), entry.identifier(), copyVariables(entry.request().variables()));
            } else if (now <= entry.enqueuedTick()) {
                pending.add(entry);
            } else {
                warnOnce("anchor:" + bound.ownerUuid() + ':' + bound.targetKind() + ':' + bound.targetName(),
                        "[Particle] Unable to resolve bound-effect anchor; dropping '{}'", entry.identifier());
            }
        }
    }

    /**
     * Preserves the native SpawnParticleEffect entity-anchor contract: XYZ is an entity-local
     * offset, while a packet without an entity uses the same XYZ fields as an absolute world point.
     */
    public boolean spawnEntity(String rawIdentifier, UUID ownerUuid,
                               float offsetX, float offsetY, float offsetZ,
                               Map<String, Float> variables) {
        if (ownerUuid == null) return false;
        final ParticleSpawnResult result = spawnResult(spawnParticleEffectEntityRequest(
                rawIdentifier, ownerUuid, offsetX, offsetY, offsetZ, variables));
        return result == ParticleSpawnResult.SPAWNED || result == ParticleSpawnResult.QUEUED;
    }

    static BedrockParticleRequest spawnParticleEffectEntityRequest(
            String rawIdentifier, UUID ownerUuid, float offsetX, float offsetY, float offsetZ,
            Map<String, Float> variables) {
        return BedrockParticleRequest.builder(rawIdentifier)
                .boundEffect(ownerUuid, BedrockParticlePlacement.TargetKind.ENTITY, "",
                        new org.joml.Vector3f(offsetX, offsetY, offsetZ))
                .variables(variables)
                .source("viabedrock-v2-entity")
                .build();
    }

    /** Clears one-shot diagnostics after a resource-pack generation swap. */
    public void clearDiagnostics() {
        warned.clear();
        pending.clear();
        poseProvider.clear();
    }

    /**
     * Bedrock particle identifiers are resource identifiers. Bare names use the vanilla
     * namespace, matching the existing payload and BEParticle loaders.
     */
    public static String normalizeIdentifier(String rawIdentifier) {
        if (rawIdentifier == null || rawIdentifier.isBlank()) {
            return null;
        }
        final String candidate = rawIdentifier.trim().toLowerCase(Locale.ROOT);
        try {
            return ResourceLocation.parse(candidate).toString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Map<String, Float> copyVariables(Map<String, Float> variables) {
        if (variables == null || variables.isEmpty()) {
            return null;
        }
        // Do not retain an integration's mutable map beyond the spawn call. LinkedHashMap keeps
        // diagnostics and MoLang scope ordering stable without imposing a caller-side type.
        return Collections.unmodifiableMap(new LinkedHashMap<>(variables));
    }

    private static AnchorSample toAnchorSample(BedrockPoseSnapshot snapshot) {
        return new AnchorSample(snapshot.position(), snapshot.rotation(), snapshot.linearVelocity(),
                snapshot.valid(), snapshot.scale(), snapshot.simulationRotation(),
                snapshot.continuationRotation());
    }

    private static void logResolvedPose(BedrockParticleRequest request, String identifier,
                                        BedrockParticlePlacement.BoundEffect bound,
                                        BedrockPoseSnapshot snapshot) {
        ViaBedrockUtilityNeoForge.LOGGER.debug(
                "[Particle:L4] resolved semantic=BOUND_EFFECT id={} source={} owner={} anchor={}/{} "
                        + "orientationPolicy={} position={} positionRotation={} simulationRotation={} continuationRotation={} "
                        + "scale={} velocity={} tick={}",
                identifier, request.source(), bound.ownerUuid(), bound.targetKind(), bound.targetName(),
                bound.orientationPolicy(), snapshot.position(), snapshot.rotation(), snapshot.simulationRotation(),
                snapshot.continuationRotation(), snapshot.scale(), snapshot.linearVelocity(), snapshot.tick());
    }

    private static Map<String, Float> actorVariables(BedrockParticlePlacement.BoundEffect bound) {
        final Minecraft minecraft = Minecraft.getInstance();
        final var level = minecraft.level;
        if (level == null) return Map.of();
        Entity entity = level.getPlayerByUUID(bound.ownerUuid());
        if (entity == null) {
            for (Entity candidate : level.entitiesForRendering()) {
                if (!candidate.isRemoved() && candidate.getUUID().equals(bound.ownerUuid())) {
                    entity = candidate;
                    break;
                }
            }
        }
        if (entity == null) return Map.of();
        final Map<String, Float> values = new LinkedHashMap<>();
        final float bodyYaw = entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
        values.put("target_x_rotation", entity.getXRot());
        values.put("target_y_rotation", BedrockParticleFrames.relativeTargetYaw(entity.getYRot(), bodyYaw));
        values.put("body_y_rotation", bodyYaw);
        values.put("position_x", (float) entity.getX());
        values.put("position_y", (float) entity.getY());
        values.put("position_z", (float) entity.getZ());
        values.put("is_first_person", minecraft.player == entity
                && minecraft.options.getCameraType().isFirstPerson() ? 1.0F : 0.0F);
        return values;
    }

    private static long currentTick() {
        final var level = Minecraft.getInstance().level;
        return level == null ? Long.MIN_VALUE : level.getGameTime();
    }

    private record PendingBoundEffect(BedrockParticleRequest request, String identifier, long enqueuedTick) {}

    private void warnOnce(String key, String message, String value) {
        if (warned.add(key)) {
            ViaBedrockUtilityNeoForge.LOGGER.warn(message, value);
        }
    }
}
