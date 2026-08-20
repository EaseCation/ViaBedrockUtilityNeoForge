package org.oryxel.viabedrockutility.particle;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Immutable world-space sample consumed by the lower particle runtime.
 *
 * <p>{@code rotation} transforms emitter-local points. Simulation and continuation rotations are
 * deliberately independent so a bone can locate an effect without rotating its world trajectory.</p>
 */
public record BedrockPoseSnapshot(Vector3f position, Quaternionf rotation,
                                  Quaternionf simulationRotation, Quaternionf continuationRotation,
                                  Vector3f scale, Vector3f linearVelocity, long tick, boolean valid) {
    /** Preserves the original constructor for providers whose three orientation contracts match. */
    public BedrockPoseSnapshot(Vector3f position, Quaternionf rotation, Vector3f scale,
                               Vector3f linearVelocity, long tick, boolean valid) {
        this(position, rotation, rotation, rotation, scale, linearVelocity, tick, valid);
    }

    public BedrockPoseSnapshot {
        position = position == null ? new Vector3f() : new Vector3f(position);
        rotation = rotation == null ? new Quaternionf() : new Quaternionf(rotation);
        simulationRotation = simulationRotation == null
                ? new Quaternionf(rotation) : new Quaternionf(simulationRotation);
        continuationRotation = continuationRotation == null
                ? new Quaternionf(simulationRotation) : new Quaternionf(continuationRotation);
        scale = scale == null ? new Vector3f(1.0F) : new Vector3f(scale);
        linearVelocity = linearVelocity == null ? new Vector3f() : new Vector3f(linearVelocity);
    }

    public static BedrockPoseSnapshot unresolved(long tick) {
        return new BedrockPoseSnapshot(null, null, null, null, null, null, tick, false);
    }

    @Override public Vector3f position() { return new Vector3f(position); }
    @Override public Quaternionf rotation() { return new Quaternionf(rotation); }
    @Override public Quaternionf simulationRotation() { return new Quaternionf(simulationRotation); }
    @Override public Quaternionf continuationRotation() { return new Quaternionf(continuationRotation); }
    @Override public Vector3f scale() { return new Vector3f(scale); }
    @Override public Vector3f linearVelocity() { return new Vector3f(linearVelocity); }
}
