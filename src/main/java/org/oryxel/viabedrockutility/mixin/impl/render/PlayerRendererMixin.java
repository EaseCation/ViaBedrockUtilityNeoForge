package org.oryxel.viabedrockutility.mixin.impl.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.mixin.interfaces.ICustomPlayerRendererHolder;
import org.oryxel.viabedrockutility.renderer.PlayerTranslucencyBufferSource;
import org.oryxel.viabedrockutility.renderer.PlayerTranslucencyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
            at = @At("TAIL"))
    private void viaBedrockUtility$extractVisualState(AbstractClientPlayer player, PlayerRenderState state,
                                                     float partialTick, CallbackInfo ci) {
        ((ICustomPlayerRendererHolder) state).viaBedrockUtility$setCustomSpectator(
                ViaBedrockUtility.getInstance().getPayloadHandler().isPlayerTranslucent(player.getUUID()));
    }

    @WrapMethod(method = "render(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void viaBedrockUtility$translucentPlayer(PlayerRenderState state, PoseStack poses,
                                                    MultiBufferSource source, int light, Operation<Void> original) {
        if (!state.isInvisible && ((ICustomPlayerRendererHolder) state).viaBedrockUtility$isCustomSpectator()
                && PlayerTranslucencyRenderer.isCapturing()) {
            PlayerTranslucencyBufferSource player = PlayerTranslucencyRenderer.capture(source, state.distanceToCameraSq);
            original.call(state, poses, player, light);
            PlayerTranslucencyRenderer.submit(player);
        } else {
            original.call(state, poses, source, light);
        }
    }

    @ModifyVariable(method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), argsOnly = true)
    private MultiBufferSource viaBedrockUtility$opaqueNameTag(MultiBufferSource source) {
        return PlayerTranslucencyBufferSource.unwrap(source);
    }
}
