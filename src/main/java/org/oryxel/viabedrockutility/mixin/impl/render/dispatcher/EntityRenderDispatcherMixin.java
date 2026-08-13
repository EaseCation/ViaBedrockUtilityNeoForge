package org.oryxel.viabedrockutility.mixin.impl.render.dispatcher;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.oryxel.viabedrockutility.renderer.CustomEntityRenderer;
import net.minecraft.world.entity.Entity;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.mixin.interfaces.ICustomPlayerRendererHolder;
import org.oryxel.viabedrockutility.renderer.EntityRendererLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("unchecked")
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    // Intercept both custom player renderers and custom entity renderers before vanilla lookup occurs.
    @Inject(method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;", at = @At("HEAD"), cancellable = true)
    public <T extends Entity> void getRenderer(T entity, CallbackInfoReturnable<EntityRenderer<? super T, ?>> cir) {
        if (!ViaBedrockUtility.getInstance().isViaBedrockPresent()) {
            return;
        }

        final EntityRenderer<?, ?> renderer = EntityRendererLookup.find(
                entity.getUUID(),
                ViaBedrockUtility.getInstance().getPayloadHandler()
        );
        if (renderer != null) {
            cir.setReturnValue((EntityRenderer<? super T, ?>) renderer);
        }
    }

    // Phase 2: two-phase rendering calls getRenderer(EntityRenderState)
    // to look up the renderer by state.entityType. Since custom entities use vanilla entity types,
    // the vanilla renderer would be returned instead of ours. We intercept this to return the
    // custom renderer stored in the state during Phase 1's extractRenderState().
    @Inject(method = "getRenderer(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)Lnet/minecraft/client/renderer/entity/EntityRenderer;", at = @At("HEAD"), cancellable = true)
    public <S extends EntityRenderState> void getRendererFromState(S state, CallbackInfoReturnable<EntityRenderer<?, ? super S>> cir) {
        if (state instanceof ICustomPlayerRendererHolder holder && holder.viaBedrockUtility$getCustomPlayerRenderer() != null) {
            cir.setReturnValue((EntityRenderer<?, ? super S>) holder.viaBedrockUtility$getCustomPlayerRenderer());
            return;
        }
        if (state instanceof CustomEntityRenderer.CustomEntityRenderState customState && customState.getCustomRenderer() != null) {
            cir.setReturnValue((EntityRenderer<?, ? super S>) customState.getCustomRenderer());
        }
    }
}
