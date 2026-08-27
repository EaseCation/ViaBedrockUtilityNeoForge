package org.oryxel.viabedrockutility.attachable;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import org.oryxel.viabedrockutility.renderer.BedrockPlayerModelMetadata;

/** Renders one VBU actor arm in first person without resetting or rewriting the shared player model. */
public final class FirstPersonBedrockArmRenderer {
    private FirstPersonBedrockArmRenderer() {
    }

    public static boolean render(AttachableHostContext host, HumanoidArm arm,
                                 ResourceLocation texture, PoseStack poses,
                                 MultiBufferSource buffers, int packedLight) {
        return render(host, arm, texture, poses, buffers, packedLight, 0.0F);
    }

    public static boolean render(AttachableHostContext host, HumanoidArm arm,
                                 ResourceLocation texture, PoseStack poses,
                                 MultiBufferSource buffers, int packedLight, float attackTime) {
        final BedrockPlayerModelMetadata.Bone armBone = host.armBone(arm);
        if (armBone == null) {
            return false;
        }

        poses.pushPose();
        try {
            poses.mulPose(host.firstPersonArmRenderPrefix(armBone, attackTime));
            armBone.part().render(poses, buffers.getBuffer(RenderType.entityTranslucent(texture)),
                    packedLight, OverlayTexture.NO_OVERLAY);
            return true;
        } finally {
            poses.popPose();
        }
    }
}
