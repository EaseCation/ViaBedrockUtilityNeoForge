package org.oryxel.viabedrockutility.attachable;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.renderer.BedrockPlayerModelMetadata;

import java.util.Locale;

/**
 * Versioned first-person host profile. The camera and arm pose match Bedrock's multi-file preview,
 * while item bones use the runtime player animation formula. Hierarchy and pivots always come from
 * the active VBU player geometry.
 */
public final class BedrockFirstPersonView {
    public static final String PROFILE_NAME = "bedrock_multifile_binding";

    // Blockbench js/formats/bedrock/attachable_preview.js camera_preset_1st_mf and
    // js/formats/bedrock/bedrock_multi_file.ts DEFAULT_POSE_FIRST.
    public static final BedrockFirstPersonView STANDARD = new BedrockFirstPersonView(
            new CameraPose(new Vec3(0.0F, 27.41F, 0.0F), new Vec3(0.0F, 27.41F, 10.0F)),
            new BonePose(new Vec3(-13.5F, -10.0F, 12.0F),
                    new Vec3(-95.0F, 45.0F, 115.0F)),
            new BonePose(new Vec3(0.0F, -7.0F, 0.0F), null),
            FirstPersonHostMeshPolicy.HIDDEN
    );

    private final CameraPose camera;
    private final BonePose rightArmPose;
    private final BonePose rightItemPose;
    private final FirstPersonHostMeshPolicy hostMeshPolicy;

    public BedrockFirstPersonView(CameraPose camera, BonePose rightArmPose, BonePose rightItemPose) {
        this(camera, rightArmPose, rightItemPose, FirstPersonHostMeshPolicy.HIDDEN);
    }

    public BedrockFirstPersonView(CameraPose camera, BonePose rightArmPose, BonePose rightItemPose,
                                  FirstPersonHostMeshPolicy hostMeshPolicy) {
        this.camera = camera;
        this.rightArmPose = rightArmPose;
        this.rightItemPose = rightItemPose;
        this.hostMeshPolicy = hostMeshPolicy;
    }

    public FirstPersonHostMeshPolicy hostMeshPolicy() {
        return hostMeshPolicy;
    }

    public Matrix4f cameraMatrix() {
        return BedrockTransformConvention.blockbenchSceneToRenderSpace(camera.viewMatrix());
    }

    /** Returns the semantic local deformation for one bone in the synthetic first-person host pose. */
    public Matrix4f localMatrix(BedrockPlayerModelMetadata metadata,
                                BedrockPlayerModelMetadata.Bone bone) {
        return localMatrix(metadata, bone, 0.0F);
    }

    /** Returns the first-person host pose with Bedrock's empty-hand attack layer applied. */
    public Matrix4f localMatrix(BedrockPlayerModelMetadata metadata,
                                BedrockPlayerModelMetadata.Bone bone, float attackTime) {
        final BonePose override = overrideFor(metadata, bone);
        final Vector3f offset = override == null
                ? new Vector3f()
                : BedrockTransformConvention.blockbenchVectorToJavaModel(override.position().vector());
        final Vector3f rotation = override == null || override.rotation() == null
                ? new Vector3f(bone.rotation())
                : BedrockTransformConvention.blockbenchRotationToJavaModel(override.rotation().vector());
        final String normalized = bone.key().replace("_", "").toLowerCase(Locale.ROOT);
        if (normalized.equals("rightarm") || normalized.equals("leftarm")) {
            AttackPose attack = attackPose(attackTime);
            if (normalized.equals("leftarm")) {
                attack = attack.mirroredX();
            }
            offset.add(BedrockTransformConvention.bedrockBindingOffsetToJavaModel(
                    attack.position().vector()));
            rotation.add(BedrockTransformConvention.bedrockAnimationRotationToJavaModel(
                    attack.rotation().vector()));
        }
        return BedrockTransformConvention.deformation(
                bone.pivot(), offset, rotation, new Vector3f(1.0F));
    }

    /** Samples animation.player.first_person.attack_rotation in Bedrock source coordinates. */
    static AttackPose attackPose(float attackTime) {
        final float time = Math.max(0.0F, Math.min(1.0F, attackTime));
        if (time == 0.0F || time == 1.0F) {
            return AttackPose.ZERO;
        }

        final double rotationFactor = sinDegrees((1.0D - time) * 180.0D);
        final double positionPhase = sinDegrees(rotationFactor * time * 112.0D);
        final float x = (float) (Math.max(-7.0D, Math.min(999.0D,
                -15.5D * positionPhase)) * positionPhase);
        final float y = (float) (sinDegrees(rotationFactor * (1.0D - time)
                * (1.0D - time) * 200.0D) * 7.5D - rotationFactor * time * 15.0D);
        final float z = (float) (sinDegrees(rotationFactor * time * 120.0D) * 1.75D);

        final double rotationPhase = sinDegrees(rotationFactor * (1.0D - time)
                * (1.0D - time) * 280.0D);
        return new AttackPose(
                new Vec3(x, y, z),
                new Vec3((float) (-60.0D * rotationPhase),
                        (float) (40.0D * rotationPhase),
                        (float) (20.0D * rotationPhase)));
    }

    private static double sinDegrees(double degrees) {
        return Math.sin(Math.toRadians(degrees));
    }

    private BonePose overrideFor(BedrockPlayerModelMetadata metadata,
                                 BedrockPlayerModelMetadata.Bone bone) {
        return switch (bone.key().replace("_", "").toLowerCase(Locale.ROOT)) {
            case "rightarm" -> rightArmPose;
            case "leftarm" -> rightArmPose.mirroredX();
            case "rightitem", "leftitem" -> runtimeItemPose(metadata, bone);
            default -> null;
        };
    }

    /**
     * Bedrock first_person.empty_hand evaluates
     * {@code [0, pivot(arm,y) - pivot(item,y) - 7, -pivot(item,z)]} in source geometry space.
     * Metadata pivots already have Y reflected around the player presentation origin, so the source
     * Y delta is {@code item.y - arm.y}; the origin cancels out.
     */
    private BonePose runtimeItemPose(BedrockPlayerModelMetadata metadata,
                                     BedrockPlayerModelMetadata.Bone itemBone) {
        final BedrockPlayerModelMetadata.Bone armBone = metadata.bone(itemBone.parentKey());
        if (armBone == null) {
            return itemBone.key().startsWith("left") ? rightItemPose.mirroredX() : rightItemPose;
        }
        return new BonePose(new Vec3(
                0.0F,
                itemBone.pivot().y - armBone.pivot().y - 7.0F,
                -itemBone.pivot().z), null);
    }

    public record Vec3(float x, float y, float z) {
        private Vector3f vector() {
            return new Vector3f(x, y, z);
        }
    }

    public record CameraPose(Vec3 position, Vec3 target) {
        private Matrix4f viewMatrix() {
            return new Matrix4f().lookAt(
                    position.x(), position.y(), position.z(),
                    target.x(), target.y(), target.z(),
                    0.0F, 1.0F, 0.0F);
        }
    }

    public record BonePose(Vec3 position, Vec3 rotation) {
        private BonePose mirroredX() {
            return new BonePose(
                    new Vec3(-position.x(), position.y(), position.z()),
                    rotation == null ? null : new Vec3(rotation.x(), -rotation.y(), -rotation.z()));
        }
    }

    record AttackPose(Vec3 position, Vec3 rotation) {
        private static final AttackPose ZERO = new AttackPose(
                new Vec3(0.0F, 0.0F, 0.0F), new Vec3(0.0F, 0.0F, 0.0F));

        private AttackPose mirroredX() {
            return new AttackPose(
                    new Vec3(-position.x(), position.y(), position.z()),
                    new Vec3(rotation.x(), -rotation.y(), -rotation.z()));
        }
    }
}
