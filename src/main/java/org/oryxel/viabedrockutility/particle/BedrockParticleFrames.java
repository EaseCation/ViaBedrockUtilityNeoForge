package org.oryxel.viabedrockutility.particle;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.attachable.BedrockTransformConvention;

/** Pure orientation policy shared by Minecraft pose extraction and particle actor queries. */
final class BedrockParticleFrames {
    private BedrockParticleFrames() {}

    record PlacedFrame(Vector3f position, Quaternionf rotation) {}

    /** Maps Bedrock local forward (-Z) into the owner's Java world-space body frame. */
    static Quaternionf ownerRootRotation(float bodyYawDegrees, Quaternionf localRotation) {
        return new Quaternionf()
                .rotationY((float) Math.toRadians(180.0F - bodyYawDegrees))
                .mul(localRotation)
                .normalize();
    }

    static Quaternionf simulationRotation(BedrockParticlePlacement.OrientationPolicy policy,
                                           Quaternionf targetPoseRotation,
                                           Quaternionf ownerRootRotation,
                                           Quaternionf ownerViewRotation,
                                           Quaternionf worldRotation) {
        return switch (policy) {
            case OWNER_ROOT -> new Quaternionf(ownerRootRotation);
            case OWNER_VIEW -> new Quaternionf(ownerViewRotation);
            case WORLD -> new Quaternionf(worldRotation);
            case TARGET_POSE -> new Quaternionf(targetPoseRotation);
        };
    }

    static float relativeTargetYaw(float headYawDegrees, float bodyYawDegrees) {
        float wrapped = (headYawDegrees - bodyYawDegrees) % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }

    /** Resolves the offset against the local-axis contract declared by the resolved anchor. */
    static Vector3f localOffset(BedrockParticlePlacement.BoundEffect placement,
                                BedrockTransformConvention.BindingOffsetFrame frame) {
        final Vector3f offset = placement.localOffset();
        return placement.targetKind() == BedrockParticlePlacement.TargetKind.ENTITY
                ? offset : BedrockTransformConvention.bedrockBindingOffset(offset, frame);
    }

    /** Applies T(base) * R(frame) * T(offset) * R(local) without rotating offset by R(local). */
    static PlacedFrame placeLocalFrame(Vector3f base, Quaternionf frameRotation,
                                       Vector3f localOffset, Quaternionf localRotation) {
        return new PlacedFrame(
                new Vector3f(localOffset).rotate(frameRotation).add(base),
                new Quaternionf(frameRotation).mul(localRotation).normalize());
    }
}
