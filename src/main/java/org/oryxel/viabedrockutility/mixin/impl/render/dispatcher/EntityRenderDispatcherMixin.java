package org.oryxel.viabedrockutility.mixin.impl.render.dispatcher;

//? if >=1.21.9 {
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
//?} else {
/*import net.minecraft.client.render.entity.EntityRenderDispatcher;
*///?}
import net.minecraft.client.renderer.entity.EntityRenderer;
//? if >=1.21.9 {
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.oryxel.viabedrockutility.renderer.CustomEntityRenderer;
//?}
import net.minecraft.world.entity.Entity;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.entity.CustomEntityTicker;
import org.oryxel.viabedrockutility.fabric.ViaBedrockUtilityFabric;
import org.oryxel.viabedrockutility.mixin.interfaces.ICustomPlayerRendererHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("unchecked")
//? if >=1.21.9 {
@Mixin(EntityRenderDispatcher.class)
//?} else {
/*@Mixin(EntityRenderDispatcher.class)
*///?}
public abstract class EntityRenderDispatcherMixin {
    //? if >=1.21.9 {
    // In 1.21.9+, getRenderer(Entity) uses switch pattern matching and delegates player rendering
    // to a private getPlayerRenderer(Map, PlayerLikeEntity) method. We inject at HEAD to intercept
    // both custom player renderers and custom entity renderers before any lookup occurs.
    private int mixinLogCounter = 0;
    @Inject(method = "getRenderer(Lnet.minecraft.world.entity.Entity;)Lnet.minecraft.client.renderer.entity.EntityRenderer;", at = @At("HEAD"), cancellable = true)
    public <T extends Entity> void getRenderer(T entity, CallbackInfoReturnable<EntityRenderer<? super T, ?>> cir) {
        if (!ViaBedrockUtility.getInstance().isViaBedrockPresent()) {
            return;
        }

        final EntityRenderer<?, ?> playerRenderer = ViaBedrockUtility.getInstance().getPayloadHandler().getCachedPlayerRenderers().get(entity.getUUID());
        if (playerRenderer != null) {
            cir.setReturnValue((EntityRenderer<? super T, ?>) playerRenderer);
            return;
        }

        final CustomEntityTicker data = ViaBedrockUtility.getInstance().getPayloadHandler().getCachedCustomEntities().get(entity.getUUID());
        if (data != null && data.getRenderer() != null) {
            if (mixinLogCounter++ % 200 == 0) {
                ViaBedrockUtilityFabric.LOGGER.debug("[Mixin] getRenderer intercepted for entity uuid={}, returning custom renderer with {} models",
                    entity.getUUID(), data.getRenderer().getModels().size());
            }
            cir.setReturnValue((EntityRenderer<? super T, ?>) data.getRenderer());
        }
    }

    // Phase 2: In 1.21.9+ two-phase rendering, pushEntityRenders calls getRenderer(EntityRenderState)
    // to look up the renderer by state.entityType. Since custom entities use vanilla entity types,
    // the vanilla renderer would be returned instead of ours. We intercept this to return the
    // custom renderer stored in the state during Phase 1's updateRenderState().
    @Inject(method = "getRenderer(Lnet.minecraft.client.renderer.entity.state.EntityRenderState;)Lnet.minecraft.client.renderer.entity.EntityRenderer;", at = @At("HEAD"), cancellable = true)
    public <S extends EntityRenderState> void getRendererFromState(S state, CallbackInfoReturnable<EntityRenderer<?, ? super S>> cir) {
        if (state instanceof ICustomPlayerRendererHolder holder && holder.viaBedrockUtility$getCustomPlayerRenderer() != null) {
            cir.setReturnValue((EntityRenderer<?, ? super S>) holder.viaBedrockUtility$getCustomPlayerRenderer());
            return;
        }
        if (state instanceof CustomEntityRenderer.CustomEntityRenderState customState && customState.getCustomRenderer() != null) {
            cir.setReturnValue((EntityRenderer<?, ? super S>) customState.getCustomRenderer());
        }
    }
    //?} else {
    /*@Inject(method = "getRenderer", at = @At(value = "INVOKE", target = "Lnet.minecraft.client.resources.PlayerSkin;model()Lnet.minecraft.client.resources.PlayerSkin$Model;"), cancellable = true)
    public <T extends Entity> void getPlayerRenderer(T entity, CallbackInfoReturnable<EntityRenderer<? super T, ?>> cir) {
        if (!ViaBedrockUtility.getInstance().isViaBedrockPresent()) {
            return;
        }

        final EntityRenderer<?, ?> renderer = ViaBedrockUtility.getInstance().getPayloadHandler().getCachedPlayerRenderers().get(entity.getUUID());
        if (renderer != null) {
            cir.setReturnValue((EntityRenderer<? super T, ?>) renderer);
        }
    }

    @Inject(method = "getRenderer", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 2), cancellable = true)
    public <T extends Entity> void getRenderer(T entity, CallbackInfoReturnable<EntityRenderer<? super T, ?>> cir) {
        if (!ViaBedrockUtility.getInstance().isViaBedrockPresent()) {
            return;
        }

        final CustomEntityTicker data = ViaBedrockUtility.getInstance().getPayloadHandler().getCachedCustomEntities().get(entity.getUUID());
        if (data != null && data.getRenderer() != null) {
            cir.setReturnValue((EntityRenderer<? super T, ?>) data.getRenderer());
        }
    }
    *///?}
}
