package org.oryxel.viabedrockutility.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.resources.ResourceLocation;
import org.oryxel.viabedrockutility.mixin.interfaces.ICustomPlayerRendererHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CustomPlayerRenderer extends PlayerRenderer {
    private final ResourceLocation texture;
    private final List<AnimatedSkinOverlay> overlays = new ArrayList<>();

    public CustomPlayerRenderer(final EntityRendererProvider.Context ctx, final PlayerModel model, final boolean slim, ResourceLocation texture) {
        super(ctx, slim);

        if (model != null) {
            this.model = model;
        }

        this.texture = texture;
        this.layers.removeIf(layer -> layer instanceof PlayerItemInHandLayer<?, ?>);
        this.addLayer(new BedrockPlayerItemInHandLayer(this));
        this.addLayer(new AnimatedOverlayFeatureRenderer(this));
    }

    @Override
    public PlayerRenderState createRenderState() {
        PlayerRenderState state = super.createRenderState();
        ((ICustomPlayerRendererHolder) state).viaBedrockUtility$setCustomPlayerRenderer(this);
        return state;
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerRenderState PlayerRenderState) {
        return this.texture;
    }

    public List<AnimatedSkinOverlay> getOverlays() {
        return Collections.unmodifiableList(overlays);
    }

    public void addOverlay(AnimatedSkinOverlay overlay) {
        this.overlays.add(overlay);
    }

    public void tickOverlays() {
        for (AnimatedSkinOverlay overlay : overlays) {
            overlay.tick();
        }
    }
}
