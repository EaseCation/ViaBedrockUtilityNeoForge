package org.oryxel.viabedrockutility.mixin.impl.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.oryxel.viabedrockutility.renderer.BedrockEntityRenderSpace;
import org.oryxel.viabedrockutility.renderer.CustomPlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Layer code may translate a head/arm directly, without going through ModelPart.render. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRenderSpaceMixin {
    @WrapOperation(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/EntityRenderState;FF)V"))
    private void vbu$layerEntitySpace(RenderLayer<?, ?> layer, PoseStack poses, MultiBufferSource buffers,
                                      int light, EntityRenderState state, float yaw, float pitch,
                                      Operation<Void> original) {
        if ((Object)this instanceof CustomPlayerRenderer) {
            try (var space = BedrockEntityRenderSpace.enter(poses, null)) {
                original.call(layer, poses, buffers, light, state, yaw, pitch);
            }
        } else {
            original.call(layer, poses, buffers, light, state, yaw, pitch);
        }
    }
}
