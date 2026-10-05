package org.oryxel.viabedrockutility.mixin.impl.world;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.oryxel.viabedrockutility.entity.CustomEntityTicker;
import org.oryxel.viabedrockutility.enums.bedrock.ActorFlags;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.payload.PayloadHandler;
import org.oryxel.viabedrockutility.renderer.CustomPlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientWorldMixin {
    @Inject(method = "tickNonPassenger", at = @At("RETURN"))
    private void tickCustomEntityMotion(Entity entity, CallbackInfo ci) {
        updateCustomEntityMotion(entity);
    }

    @Inject(method = "tickPassenger", at = @At("RETURN"))
    private void tickPassengerMotion(Entity vehicle, Entity passenger, CallbackInfo ci) {
        updateCustomEntityMotion(passenger);
    }

    @Unique
    private static void updateCustomEntityMotion(Entity entity) {
        if (!ViaBedrockUtility.getInstance().isViaBedrockPresent() || entity.isRemoved()) {
            return;
        }
        final CustomEntityTicker ticker = ViaBedrockUtility.getInstance().getPayloadHandler()
                .getCachedCustomEntities().get(entity.getUUID());
        if (ticker != null) {
            ticker.getMotion().tick(entity.tickCount, entity.getX(), entity.getY(), entity.getZ(),
                    entity.getYRot(), entity.isPassenger() || ticker.entityFlags().contains(ActorFlags.RIDING));
        }
        if (entity instanceof AbstractClientPlayer player) {
            final EntityRenderer<?, ?> renderer = ViaBedrockUtility.getInstance().getPayloadHandler()
                    .cachedPlayerRenderer(player.getUUID());
            if (renderer instanceof CustomPlayerRenderer customRenderer) {
                customRenderer.motionState(player).tick(player.tickCount,
                        player.getX(), player.getY(), player.getZ(), player.yBodyRot, player.isPassenger());
            }
        }
    }

    @Inject(method = "removeEntity", at = @At(value = "HEAD"))
    private void injectRemoveEntity(int entityId, Entity.RemovalReason removalReason, CallbackInfo ci) {
        if (!ViaBedrockUtility.getInstance().isViaBedrockPresent()) {
            return;
        }

        final PayloadHandler handler = ViaBedrockUtility.getInstance().getPayloadHandler();

        Entity entity = ((ClientLevel) (Object) this).getEntity(entityId);
        if (entity != null) {
            handler.removeCustomEntity(entity.getUUID());
        }
    }
}
