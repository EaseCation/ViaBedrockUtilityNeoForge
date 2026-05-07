package org.oryxel.viabedrockutility.mixin.impl.render.feature;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.resources.ResourceLocation;
import org.oryxel.viabedrockutility.fabric.ViaBedrockUtilityFabric;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CapeLayer.class)
public class CapeFeatureRendererMixin {
    @Redirect(
            method = "render(Lcom.mojang.blaze3d.vertex.PoseStack;Lnet.minecraft.client.renderer.MultiBufferSource;ILnet.minecraft.client.renderer.entity.state.PlayerRenderState;FF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet.minecraft.client.renderer.RenderType;entitySolid(Lnet.minecraft.resources.ResourceLocation;)Lnet.minecraft.client.renderer.RenderType;"),
            require = 0
    )
    public RenderType solidToTranslucent(final ResourceLocation texture) {
        if (texture.getNamespace().equals(ViaBedrockUtilityFabric.MOD_ID)) {
            return RenderType.entityTranslucent(texture, true);
        }
        return RenderType.entitySolid(texture);
    }
}
