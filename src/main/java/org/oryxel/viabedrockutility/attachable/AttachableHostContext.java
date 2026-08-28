package org.oryxel.viabedrockutility.attachable;

import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.renderer.BedrockModelPartTransform;
import org.oryxel.viabedrockutility.renderer.BedrockPlayerModelMetadata;

import java.util.List;
import java.util.function.Function;

/** Resolves Bedrock player binding bones and their bind/current deformation matrices. */
public final class AttachableHostContext {
    public static final String FIRST_PERSON_PROFILE = "bedrock_player_runtime";
    private static final Matrix4f FIRST_PERSON_CAMERA =
            BedrockTransformConvention.blockbenchSceneToRenderSpace(new Matrix4f().lookAt(
                    0.0F, 27.41F, 0.0F,
                    0.0F, 27.41F, 10.0F,
                    0.0F, 1.0F, 0.0F));
    private final BedrockPlayerModelMetadata metadata;

    public AttachableHostContext(BedrockPlayerModelMetadata metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("Attachable host requires VBU BedrockPlayerModelMetadata");
        }
        this.metadata = metadata;
    }

    public BedrockPlayerModelMetadata.Bone itemBone(AttachableQueryContext.LogicalHand hand,
                                                    HumanoidArm physicalArm) {
        return metadata.firstBone(physicalArm == HumanoidArm.RIGHT ? "rightitem" : "leftitem");
    }

    public BedrockPlayerModelMetadata.Bone armBone(HumanoidArm physicalArm) {
        return physicalArm == HumanoidArm.RIGHT
                ? metadata.firstBone("rightarm", "right_arm")
                : metadata.firstBone("leftarm", "left_arm");
    }

    public BedrockPlayerModelMetadata.Bone bindingBone(String binding,
                                                       AttachableQueryContext.LogicalHand hand,
                                                       HumanoidArm physicalArm) {
        final String normalized = BedrockPlayerModelMetadata.normalize(binding);
        if (normalized.isBlank()) {
            return itemBone(hand, physicalArm);
        }
        return metadata.firstBone(normalized);
    }

    public Matrix4f bindWorldMatrix(BedrockPlayerModelMetadata.Bone bone) {
        return worldMatrix(metadata.chainTo(bone), false);
    }

    public Matrix4f currentWorldMatrix(BedrockPlayerModelMetadata.Bone bone) {
        return worldMatrix(metadata.chainTo(bone), true);
    }

    public Matrix4f deformationMatrix(BedrockPlayerModelMetadata.Bone bone) {
        return BedrockTransformConvention.hostDeformation(currentWorldMatrix(bone), bindWorldMatrix(bone));
    }

    /** Complete deformation of the flattened third-person presentation chain for armor layers. */
    public Matrix4f presentationDeformationMatrix(BedrockPlayerModelMetadata.Bone bone) {
        return BedrockTransformConvention.hostDeformation(
                worldMatrix(metadata.presentationChainTo(bone), true),
                worldMatrix(metadata.presentationChainTo(bone), false));
    }

    public Matrix4f attachmentMatrix(BedrockPlayerModelMetadata.Bone bone) {
        return BedrockTransformConvention.hostAttachment(
                currentWorldMatrix(bone), bindWorldMatrix(bone), bone.pivot());
    }

    public Matrix4f firstPersonAttachmentMatrix(BedrockPlayerModelMetadata.Bone bone) {
        return BedrockTransformConvention.hostAttachment(
                firstPersonWorldMatrix(bone), bindWorldMatrix(bone), bone.pivot());
    }

    public Matrix4f firstPersonAttachmentMatrix(BedrockPlayerModelMetadata.Bone bone,
                                                Vector3f anchorPixels) {
        return BedrockTransformConvention.hostAttachment(
                firstPersonWorldMatrix(bone), bindWorldMatrix(bone), anchorPixels);
    }

    public FirstPersonHostMeshPolicy firstPersonHostMeshPolicy() {
        return FirstPersonHostMeshPolicy.HIDDEN;
    }

    static Matrix4f firstPersonCameraMatrix() {
        return new Matrix4f(FIRST_PERSON_CAMERA);
    }

    /** Prefixes a direct ModelPart render without mutating the host model's current pose. */
    public Matrix4f firstPersonArmRenderPrefix(BedrockPlayerModelMetadata.Bone armBone) {
        final String parentKey = armBone.parentKey();
        if (parentKey == null || parentKey.isBlank()) {
            return firstPersonCameraMatrix();
        }
        // PlayerModel's arm is flattened beneath root. ModelPart.render applies the arm's
        // complete current absolute transform itself, so the prefix contains only the semantic
        // Bedrock parent chain, exactly like the 26.1 PlayerBoneModel path.
        final BedrockPlayerModelMetadata.Bone parent = metadata.bone(parentKey);
        if (parent == null) {
            throw new IllegalStateException("Player arm semantic parent is missing: " + parentKey);
        }
        // The flattened ModelPart.render() consumes the arm's current absolute transform. Convert
        // that absolute transform into the arm's semantic local space against the body's bind pose
        // before applying the current semantic parent chain. This matters for player geometries with
        // a non-identity body bind rotation/scale; omitting it silently skews or pins the arm.
        return firstPersonWorldMatrix(parent)
                .mul(new Matrix4f(bindWorldMatrix(parent)).invert());
    }

    public List<String> semanticChain(BedrockPlayerModelMetadata.Bone bone) {
        return metadata.chainTo(bone).stream().map(BedrockPlayerModelMetadata.Bone::key).toList();
    }

    public List<String> presentationChain(BedrockPlayerModelMetadata.Bone bone) {
        return metadata.presentationChainTo(bone).stream().map(BedrockPlayerModelMetadata.Bone::key).toList();
    }

    private Matrix4f firstPersonWorldMatrix(BedrockPlayerModelMetadata.Bone bone) {
        return firstPersonWorldMatrix(metadata.chainTo(bone),
                entry -> localMatrix(entry, true),
                entry -> localMatrix(entry, false));
    }

    static Matrix4f firstPersonWorldMatrix(
            List<BedrockPlayerModelMetadata.Bone> chain,
            Function<BedrockPlayerModelMetadata.Bone, Matrix4f> currentAbsoluteMatrix,
            Function<BedrockPlayerModelMetadata.Bone, Matrix4f> bindAbsoluteMatrix) {
        final Matrix4f matrix = firstPersonCameraMatrix();
        matrix.mul(worldMatrix(chain, currentAbsoluteMatrix, bindAbsoluteMatrix));
        return matrix;
    }

    private static Matrix4f worldMatrix(List<BedrockPlayerModelMetadata.Bone> chain, boolean current) {
        return worldMatrix(chain,
                bone -> localMatrix(bone, current),
                bone -> localMatrix(bone, false));
    }

    /**
     * Rebuilds a semantic Bedrock chain from flattened ModelPart transforms. Each player ModelPart stores
     * an absolute bind pivot, so a child local transform is derived against its bind parent. Using the
     * current parent here would cancel parent animation instead of inheriting it.
     */
    private static Matrix4f worldMatrix(
            List<BedrockPlayerModelMetadata.Bone> chain,
            Function<BedrockPlayerModelMetadata.Bone, Matrix4f> currentAbsoluteMatrix,
            Function<BedrockPlayerModelMetadata.Bone, Matrix4f> bindAbsoluteMatrix) {
        final Matrix4f matrix = new Matrix4f();
        Matrix4f bindParent = null;
        for (BedrockPlayerModelMetadata.Bone bone : chain) {
            final Matrix4f currentAbsolute = currentAbsoluteMatrix.apply(bone);
            final Matrix4f local = bindParent == null
                    ? currentAbsolute
                    : new Matrix4f(bindParent).invert().mul(currentAbsolute);
            matrix.mul(local);
            bindParent = bindAbsoluteMatrix.apply(bone);
        }
        return matrix;
    }

    private static Matrix4f localMatrix(BedrockPlayerModelMetadata.Bone bone, boolean current) {
        if (current) {
            return BedrockModelPartTransform.current(bone.part());
        }
        return BedrockTransformConvention.deformation(
                bone.pivot(), new Vector3f(), new Vector3f(bone.rotation()), new Vector3f(1.0F));
    }
}
