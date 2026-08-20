package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;

/** Publishes the model pose only after vanilla has applied setupAnim and its renderer-entry stack. */
final class BedrockPlayerPoseCaptureLayer extends RenderLayer<PlayerRenderState, PlayerModel> {
    private final CustomPlayerRenderer renderer;

    BedrockPlayerPoseCaptureLayer(CustomPlayerRenderer renderer) {
        super(renderer);
        this.renderer = renderer;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       PlayerRenderState renderState, float yRot, float xRot) {
        renderer.captureThirdPersonPose(renderState, poseStack);
    }
}
