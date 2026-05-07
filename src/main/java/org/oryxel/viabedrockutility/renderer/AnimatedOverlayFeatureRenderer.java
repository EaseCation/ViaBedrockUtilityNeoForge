package org.oryxel.viabedrockutility.renderer;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import com.mojang.blaze3d.vertex.PoseStack;

public class AnimatedOverlayFeatureRenderer extends RenderLayer<PlayerRenderState, PlayerModel> {
    private final CustomPlayerRenderer renderer;

    public AnimatedOverlayFeatureRenderer(CustomPlayerRenderer renderer) {
        super(renderer);
        this.renderer = renderer;
    }

    @Override
    public void render(PoseStack matrices, MultiBufferSource vertexConsumers, int light, PlayerRenderState state, float limbAngle, float limbDistance) {
        if (renderer.getOverlays().isEmpty()) return;

        PlayerModel mainModel = (PlayerModel) renderer.getModel();
        for (AnimatedSkinOverlay overlay : renderer.getOverlays()) {
            overlay.copyBoneTransformsFrom(mainModel);
            RenderType renderType = RenderType.entityTranslucent(overlay.getTextureId(), true);
            VertexConsumer consumer = vertexConsumers.getBuffer(renderType);
            overlay.getModel().renderToBuffer(matrices, consumer, light, OverlayTexture.pack(0, 10));
        }
    }
}
