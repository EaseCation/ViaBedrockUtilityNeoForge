package org.oryxel.viabedrockutility.particle;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** VBU-owned, Minecraft-independent description of a particle spawn. */
public final class BedrockParticleRequest {
    private final String identifier;
    private final BedrockParticlePlacement placement;
    private final Map<String, Float> variables;
    private final String source;
    private final String preEffectExpression;
    private final long resourceGeneration;

    private BedrockParticleRequest(Builder builder) {
        this.identifier = builder.identifier;
        this.placement = Objects.requireNonNullElseGet(builder.placement,
                () -> new BedrockParticlePlacement.WorldTrajectory(new Vector3f(), new Quaternionf()));
        this.variables = Map.copyOf(new LinkedHashMap<>(builder.variables));
        this.source = builder.source;
        this.preEffectExpression = builder.preEffectExpression;
        this.resourceGeneration = builder.resourceGeneration;
    }

    public static Builder builder(String identifier) { return new Builder(identifier); }
    public String identifier() { return identifier; }
    public BedrockParticlePlacement placement() { return placement; }
    /** Legacy coordinates are meaningful only for world placements. */
    public float x() { return placement instanceof BedrockParticlePlacement.WorldTrajectory world ? world.origin().x : 0.0F; }
    public float y() { return placement instanceof BedrockParticlePlacement.WorldTrajectory world ? world.origin().y : 0.0F; }
    public float z() { return placement instanceof BedrockParticlePlacement.WorldTrajectory world ? world.origin().z : 0.0F; }
    public Map<String, Float> variables() { return variables; }
    public String source() { return source; }
    public String preEffectExpression() { return preEffectExpression; }
    /** -1 resolves against the generation current at spawn time. */
    public long resourceGeneration() { return resourceGeneration; }

    public static final class Builder {
        private final String identifier;
        private BedrockParticlePlacement placement;
        private final Map<String, Float> variables = new LinkedHashMap<>();
        private String source = "vbu";
        private String preEffectExpression;
        private long resourceGeneration = -1L;
        private Builder(String identifier) { this.identifier = identifier; }
        public Builder position(float x, float y, float z) {
            this.placement = new BedrockParticlePlacement.WorldTrajectory(
                    new Vector3f(x, y, z), new Quaternionf());
            return this;
        }
        public Builder placement(BedrockParticlePlacement placement) {
            this.placement = Objects.requireNonNull(placement, "placement");
            return this;
        }
        public Builder boundEffect(UUID ownerUuid, BedrockParticlePlacement.TargetKind targetKind,
                                   String targetName, Vector3f localOffset) {
            return boundEffect(ownerUuid, targetKind, targetName, localOffset,
                    new Quaternionf(), BedrockParticlePlacement.ViewContext.ENTITY);
        }
        public Builder boundEffect(UUID ownerUuid, BedrockParticlePlacement.TargetKind targetKind,
                                   String targetName, Vector3f localOffset, Quaternionf localRotation,
                                   BedrockParticlePlacement.ViewContext viewContext) {
            return boundEffect(ownerUuid, targetKind, targetName, localOffset, localRotation,
                    viewContext, targetKind == BedrockParticlePlacement.TargetKind.LOCATOR
                            ? BedrockParticlePlacement.OrientationPolicy.TARGET_POSE
                            : BedrockParticlePlacement.OrientationPolicy.OWNER_ROOT);
        }
        public Builder boundEffect(UUID ownerUuid, BedrockParticlePlacement.TargetKind targetKind,
                                   String targetName, Vector3f localOffset, Quaternionf localRotation,
                                   BedrockParticlePlacement.ViewContext viewContext,
                                   BedrockParticlePlacement.OrientationPolicy orientationPolicy) {
            this.placement = new BedrockParticlePlacement.BoundEffect(
                    ownerUuid, targetKind, targetName, localOffset, localRotation, viewContext,
                    orientationPolicy);
            return this;
        }
        public Builder worldTrajectory(Vector3f origin, Quaternionf orientation) {
            this.placement = new BedrockParticlePlacement.WorldTrajectory(origin, orientation);
            return this;
        }
        public Builder worldTrajectory(Vector3f origin, Vector3f forward) {
            this.placement = BedrockParticlePlacement.WorldTrajectory.fromForward(origin, forward);
            return this;
        }
        public Builder variable(String name, float value) { if (name != null && !name.isBlank()) variables.put(name, value); return this; }
        public Builder variables(Map<String, Float> values) { if (values != null) variables.putAll(values); return this; }
        public Builder source(String source) { if (source != null && !source.isBlank()) this.source = source; return this; }
        public Builder preEffectExpression(String expression) {
            this.preEffectExpression = expression == null || expression.isBlank() ? null : expression;
            return this;
        }
        public Builder resourceGeneration(long generation) {
            if (generation < 0L) throw new IllegalArgumentException("generation must be non-negative");
            this.resourceGeneration = generation;
            return this;
        }
        public BedrockParticleRequest build() { return new BedrockParticleRequest(this); }
    }
}
