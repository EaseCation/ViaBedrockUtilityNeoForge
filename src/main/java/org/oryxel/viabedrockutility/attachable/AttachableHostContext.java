package org.oryxel.viabedrockutility.attachable;

import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.renderer.BedrockModelPartTransform;
import org.oryxel.viabedrockutility.renderer.BedrockPlayerModelMetadata;

import java.util.List;

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

    public FirstPersonHostMeshPolicy firstPersonHostMeshPolicy() {
        return FirstPersonHostMeshPolicy.HIDDEN;
    }

    static Matrix4f firstPersonCameraMatrix() {
        return new Matrix4f(FIRST_PERSON_CAMERA);
    }

    /** Prefixes a direct ModelPart render without mutating the host model's current pose. */
    public Matrix4f firstPersonArmRenderPrefix(BedrockPlayerModelMetadata.Bone armBone) {
        final Matrix4f target = firstPersonWorldMatrix(armBone);
        return target.mul(localMatrix(armBone, true).invert());
    }

    public List<String> semanticChain(BedrockPlayerModelMetadata.Bone bone) {
        return metadata.chainTo(bone).stream().map(BedrockPlayerModelMetadata.Bone::key).toList();
    }

    public List<String> presentationChain(BedrockPlayerModelMetadata.Bone bone) {
        return metadata.presentationChainTo(bone).stream().map(BedrockPlayerModelMetadata.Bone::key).toList();
    }

    private Matrix4f firstPersonWorldMatrix(BedrockPlayerModelMetadata.Bone bone) {
        final Matrix4f matrix = new Matrix4f(FIRST_PERSON_CAMERA);
        for (BedrockPlayerModelMetadata.Bone entry : metadata.chainTo(bone)) {
            matrix.mul(localMatrix(entry, true));
        }
        return matrix;
    }

    private static Matrix4f worldMatrix(List<BedrockPlayerModelMetadata.Bone> chain, boolean current) {
        final Matrix4f matrix = new Matrix4f();
        for (BedrockPlayerModelMetadata.Bone bone : chain) {
            matrix.mul(localMatrix(bone, current));
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
