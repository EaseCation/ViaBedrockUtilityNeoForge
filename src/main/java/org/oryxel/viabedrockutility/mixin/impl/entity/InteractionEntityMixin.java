package org.oryxel.viabedrockutility.mixin.impl.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.entity.CustomEntityTicker;
import org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Interaction.class)
public abstract class InteractionEntityMixin extends Entity {
    @Unique
    private InterpolationHandler viaBedrockUtility$interpolation;
    @Unique
    private boolean viaBedrockUtility$advancingInterpolation;

    protected InteractionEntityMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    @Nullable
    public InterpolationHandler getInterpolation() {
        final ViaBedrockUtility utility = ViaBedrockUtility.getInstance();
        final CustomEntityPayloadHandler handler = utility.getPayloadHandler();
        if (!utility.isViaBedrockPresent() || handler == null || !handler.isCustomEntityMotionEnabled()
                || !handler.getCachedCustomEntities().containsKey(this.getUUID()) || this.isPassenger()) {
            return super.getInterpolation();
        }
        if (this.viaBedrockUtility$interpolation == null) {
            this.viaBedrockUtility$interpolation = new InterpolationHandler(this);
        }
        return this.viaBedrockUtility$interpolation;
    }

    @Inject(method = "tick", at = @At("TAIL"), require = 1)
    private void viaBedrockUtility$tickInterpolation(CallbackInfo ci) {
        final InterpolationHandler interpolation = this.getInterpolation();
        if (interpolation == null) {
            this.viaBedrockUtility$cancelInterpolation();
        } else if (interpolation.hasActiveInterpolation()) {
            this.viaBedrockUtility$advancingInterpolation = true;
            try {
                interpolation.interpolate();
            } finally {
                this.viaBedrockUtility$advancingInterpolation = false;
            }
        }
    }

    @Override
    public void setPos(double x, double y, double z) {
        if (!this.viaBedrockUtility$advancingInterpolation) {
            this.viaBedrockUtility$cancelInterpolation();
        }
        super.setPos(x, y, z);
    }

    @Override
    public void snapTo(double x, double y, double z, float yaw, float pitch) {
        this.viaBedrockUtility$cancelInterpolation();
        super.snapTo(x, y, z, yaw, pitch);
        final CustomEntityPayloadHandler handler = ViaBedrockUtility.getInstance().getPayloadHandler();
        final CustomEntityTicker ticker = handler == null ? null : handler.getCachedCustomEntities().get(this.getUUID());
        if (ticker != null) {
            ticker.getMotion().relocate(this.tickCount, x, y, z, yaw);
        }
    }

    @Unique
    private void viaBedrockUtility$cancelInterpolation() {
        if (this.viaBedrockUtility$interpolation != null) {
            this.viaBedrockUtility$interpolation.cancel();
        }
    }
}
