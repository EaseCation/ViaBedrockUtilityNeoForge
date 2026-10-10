package org.oryxel.viabedrockutility.mixin.impl.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
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
    private boolean viaBedrockUtility$interpolating;

    protected InteractionEntityMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    @Nullable
    public InterpolationHandler getInterpolation() {
        final ViaBedrockUtility utility = ViaBedrockUtility.getInstance();
        final CustomEntityPayloadHandler handler = utility.getPayloadHandler();
        if (!utility.isViaBedrockPresent() || handler == null
                || !handler.getCachedCustomEntities().containsKey(this.getUUID())) {
            return super.getInterpolation();
        }
        if (this.viaBedrockUtility$interpolation == null) {
            this.viaBedrockUtility$interpolation = new InterpolationHandler(this);
        }
        return this.viaBedrockUtility$interpolation;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void viaBedrockUtility$tickInterpolation(CallbackInfo ci) {
        final InterpolationHandler interpolation = this.getInterpolation();
        if (interpolation == null) {
            if (this.viaBedrockUtility$interpolation != null) {
                this.viaBedrockUtility$interpolation.cancel();
            }
            return;
        }
        if (interpolation.hasActiveInterpolation()) {
            this.viaBedrockUtility$interpolating = true;
            try {
                interpolation.interpolate();
            } finally {
                this.viaBedrockUtility$interpolating = false;
            }
        }
    }

    @Override
    public void setPos(double x, double y, double z) {
        if (this.viaBedrockUtility$interpolation != null && !this.viaBedrockUtility$interpolating) {
            this.viaBedrockUtility$interpolation.cancel();
        }
        super.setPos(x, y, z);
    }

    @Override
    public void snapTo(double x, double y, double z, float yaw, float pitch) {
        if (this.viaBedrockUtility$interpolation != null) {
            this.viaBedrockUtility$interpolation.cancel();
        }
        super.snapTo(x, y, z, yaw, pitch);
    }
}
