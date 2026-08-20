package org.oryxel.viabedrockutility.attachable;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.renderer.BedrockPlayerModelMetadata;

import java.util.Locale;

/**
 * Versioned first-person host profile from Blockbench's Bedrock multi-file attachable preview.
 * The profile only supplies camera and per-bone pose overrides; hierarchy and bind pivots always
 * come from the active VBU player geometry.
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
    public Matrix4f localMatrix(BedrockPlayerModelMetadata.Bone bone) {
        final BonePose override = overrideFor(bone.key());
        final Vector3f offset = override == null
                ? new Vector3f()
                : BedrockTransformConvention.blockbenchVectorToJavaModel(override.position().vector());
        final Vector3f rotation = override == null || override.rotation() == null
                ? new Vector3f(bone.rotation())
                : BedrockTransformConvention.blockbenchRotationToJavaModel(override.rotation().vector());
        return BedrockTransformConvention.deformation(
                bone.pivot(), offset, rotation, new Vector3f(1.0F));
    }

    private BonePose overrideFor(String boneName) {
        return switch (boneName.replace("_", "").toLowerCase(Locale.ROOT)) {
            case "rightarm" -> rightArmPose;
            case "leftarm" -> rightArmPose.mirroredX();
            case "rightitem" -> rightItemPose;
            case "leftitem" -> rightItemPose.mirroredX();
            default -> null;
        };
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
}
