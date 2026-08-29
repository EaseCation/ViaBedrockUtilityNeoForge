package org.oryxel.viabedrockutility.attachable;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.oryxel.viabedrockutility.renderer.BedrockPlayerModelMetadata;
import org.oryxel.viabedrockutility.renderer.BedrockModelPartTransform;

/** Renders one VBU actor arm in first person without resetting or rewriting the shared player model. */
public final class FirstPersonBedrockArmRenderer {
    private FirstPersonBedrockArmRenderer() {
    }

    public static boolean render(AttachableHostContext host, HumanoidArm arm,
                                 ResourceLocation texture, PoseStack poses,
                                 MultiBufferSource buffers, int packedLight) {
        final BedrockPlayerModelMetadata.Bone armBone = host.armBone(arm);
        if (armBone == null) {
            return false;
        }

        poses.pushPose();
        try {
            poses.mulPose(host.firstPersonArmRenderPrefix(armBone));
            FirstPersonRenderTrace.record("arm_prefix", arm, poses);
            // ModelPart.render() consumes this exact current absolute transform. Capture the
            // composed matrix before rendering so diagnostics include the arm's own Bedrock
            // position/rotation rather than only the semantic parent prefix.
            FirstPersonRenderTrace.recordMatrix("arm_submit", arm,
                    new Matrix4f(poses.last().pose()).mul(BedrockModelPartTransform.current(armBone.part())));
            armBone.part().render(poses, buffers.getBuffer(RenderType.entityTranslucent(texture)),
                    packedLight, OverlayTexture.NO_OVERLAY);
            return true;
        } finally {
            poses.popPose();
        }
    }
}
