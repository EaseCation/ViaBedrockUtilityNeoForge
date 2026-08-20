package org.oryxel.viabedrockutility.particle;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;
import java.util.UUID;

/** Explicitly separates host-bound Bedrock effects from world-space trajectories. */
public sealed interface BedrockParticlePlacement
        permits BedrockParticlePlacement.BoundEffect, BedrockParticlePlacement.WorldTrajectory {

    enum Semantic { BOUND_EFFECT, WORLD_TRAJECTORY }
    enum TargetKind { ENTITY, BONE, LOCATOR }
    enum ViewContext { ENTITY, FIRST_PERSON, THIRD_PERSON }
    enum OrientationPolicy {
        /** The owner root/body frame orients simulation; the target pose only locates the effect. */
        OWNER_ROOT,
        /** The owner's actual head yaw and pitch orient simulation, independently of render view. */
        OWNER_VIEW,
        /** localRotation is an absolute world-space simulation orientation. */
        WORLD,
        /** The complete target bone/locator frame orients both simulation and local-space updates. */
        TARGET_POSE
    }

    Semantic semantic();

    record BoundEffect(UUID ownerUuid, TargetKind targetKind, String targetName,
                       Vector3f localOffset, Quaternionf localRotation,
                       ViewContext viewContext, OrientationPolicy orientationPolicy)
            implements BedrockParticlePlacement {
        /*
         * ENTITY offsets retain the Bedrock packet's entity-root axes. BONE and LOCATOR offsets
         * retain CreateBindEntityNew's Bedrock host-model axes and are converted by the pose provider.
         */
        /** Preserves the original constructor and selects the Bedrock default for each target kind. */
        public BoundEffect(UUID ownerUuid, TargetKind targetKind, String targetName,
                           Vector3f localOffset, Quaternionf localRotation, ViewContext viewContext) {
            this(ownerUuid, targetKind, targetName, localOffset, localRotation, viewContext,
                    targetKind == TargetKind.LOCATOR
                            ? OrientationPolicy.TARGET_POSE : OrientationPolicy.OWNER_ROOT);
        }

        public BoundEffect {
            Objects.requireNonNull(ownerUuid, "ownerUuid");
            Objects.requireNonNull(targetKind, "targetKind");
            Objects.requireNonNull(viewContext, "viewContext");
            Objects.requireNonNull(orientationPolicy, "orientationPolicy");
            targetName = targetName == null ? "" : targetName.trim();
            if (targetKind != TargetKind.ENTITY && targetName.isEmpty()) {
                throw new IllegalArgumentException(targetKind + " placement requires a target name");
            }
            localOffset = localOffset == null ? new Vector3f() : new Vector3f(localOffset);
            localRotation = localRotation == null ? new Quaternionf() : new Quaternionf(localRotation);
        }

        @Override public Semantic semantic() { return Semantic.BOUND_EFFECT; }

        @Override public Vector3f localOffset() { return new Vector3f(localOffset); }
        @Override public Quaternionf localRotation() { return new Quaternionf(localRotation); }
    }

    record WorldTrajectory(Vector3f origin, Quaternionf orientation)
            implements BedrockParticlePlacement {
        public WorldTrajectory {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(orientation, "orientation");
            origin = new Vector3f(origin);
            orientation = new Quaternionf(orientation).normalize();
        }

        public static WorldTrajectory fromForward(Vector3f origin, Vector3f forward) {
            Objects.requireNonNull(forward, "forward");
            final Vector3f normalized = new Vector3f(forward);
            if (normalized.lengthSquared() < 1.0E-12F) {
                throw new IllegalArgumentException("World trajectory direction must be non-zero");
            }
            normalized.normalize();
            return new WorldTrajectory(origin,
                    new Quaternionf().rotationTo(new Vector3f(0.0F, 0.0F, -1.0F), normalized));
        }

        @Override public Semantic semantic() { return Semantic.WORLD_TRAJECTORY; }

        @Override public Vector3f origin() { return new Vector3f(origin); }
        @Override public Quaternionf orientation() { return new Quaternionf(orientation); }
    }
}
