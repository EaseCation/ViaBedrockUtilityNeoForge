package org.oryxel.viabedrockutility.mixin.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.mixin.interfaces.ICustomPlayerRendererHolder;
import org.oryxel.viabedrockutility.renderer.PlayerTranslucencyBufferSource;
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

    @ModifyVariable(method = "render(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), argsOnly = true)
    private MultiBufferSource viaBedrockUtility$translucentPlayer(MultiBufferSource source,
                                                                 PlayerRenderState state, PoseStack poses,
                                                                 MultiBufferSource originalSource, int light) {
        return !state.isInvisible && ((ICustomPlayerRendererHolder) state).viaBedrockUtility$isCustomSpectator()
                ? new PlayerTranslucencyBufferSource(source) : source;
    }

    @ModifyVariable(method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), argsOnly = true)
    private MultiBufferSource viaBedrockUtility$opaqueNameTag(MultiBufferSource source) {
        return PlayerTranslucencyBufferSource.unwrap(source);
    }
}
