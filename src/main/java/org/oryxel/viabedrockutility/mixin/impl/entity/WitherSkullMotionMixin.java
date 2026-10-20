package org.oryxel.viabedrockutility.mixin.impl.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 基岩头颅只消费服务端轨迹，避免原版火球自加速和本地碰撞反复纠正位置。 */
@Mixin(AbstractHurtingProjectile.class)
public abstract class WitherSkullMotionMixin extends Projectile {
    @Unique
    private InterpolationHandler viaBedrockUtility$skullInterpolation;
    @Unique
    private boolean viaBedrockUtility$advancingSkullInterpolation;

    protected WitherSkullMotionMixin(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
    }

    @Shadow
    @Nullable
    protected abstract ParticleOptions getTrailParticle();

    @Unique
    private boolean viaBedrockUtility$usesBedrockSkullMotion() {
        if (!((Object) this instanceof WitherSkull)) {
            return false;
        }
        final ViaBedrockUtility utility = ViaBedrockUtility.getInstance();
        final CustomEntityPayloadHandler handler = utility.getPayloadHandler();
        return utility.isViaBedrockPresent() && handler != null && handler.isWitherSkullMotionEnabled();
    }

    @Override
    @Nullable
    public InterpolationHandler getInterpolation() {
        if (!this.viaBedrockUtility$usesBedrockSkullMotion()) {
            return super.getInterpolation();
        }
        if (this.viaBedrockUtility$skullInterpolation == null) {
            // 一个 tick 的窗口跟随基岩高频位置更新，避免为反弹命中额外积累显示延迟。
            this.viaBedrockUtility$skullInterpolation = new InterpolationHandler(this, 1);
        }
        return this.viaBedrockUtility$skullInterpolation;
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 1)
    private void viaBedrockUtility$tickBedrockSkull(CallbackInfo ci) {
        if (!this.viaBedrockUtility$usesBedrockSkullMotion()) {
            this.viaBedrockUtility$cancelSkullInterpolation();
            return;
        }
        super.tick();
        this.viaBedrockUtility$advancingSkullInterpolation = true;
        try {
            this.getInterpolation().interpolate();
        } finally {
            this.viaBedrockUtility$advancingSkullInterpolation = false;
        }
        final ParticleOptions particle = this.getTrailParticle();
        if (particle != null) {
            this.level().addParticle(particle, this.getX(), this.getY() + 0.5, this.getZ(), 0, 0, 0);
        }
        ci.cancel();
    }

    @Override
    public void setPos(double x, double y, double z) {
        if (!this.viaBedrockUtility$advancingSkullInterpolation) {
            this.viaBedrockUtility$cancelSkullInterpolation();
        }
        super.setPos(x, y, z);
    }

    @Override
    public void snapTo(double x, double y, double z, float yaw, float pitch) {
        this.viaBedrockUtility$cancelSkullInterpolation();
        super.snapTo(x, y, z, yaw, pitch);
    }

    @Unique
    private void viaBedrockUtility$cancelSkullInterpolation() {
        if (this.viaBedrockUtility$skullInterpolation != null) {
            this.viaBedrockUtility$skullInterpolation.cancel();
        }
    }
}
