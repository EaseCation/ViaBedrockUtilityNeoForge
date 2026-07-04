package org.oryxel.viabedrockutility.mixin.impl.render;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.oryxel.viabedrockutility.animation.PlayerAnimationManager;
import org.oryxel.viabedrockutility.config.LodConfig;
import org.oryxel.viabedrockutility.mixin.interfaces.IBedrockAnimatedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerEntityModelMixin implements IBedrockAnimatedModel {
    @Unique
    private PlayerAnimationManager animationManager;

    // Per-model frame counter for distance-based throttle cadence (LodConfig.shouldAnimate keys on
    // frameCounter % interval). Incremented on every setupAnim that isn't short-circuited.
    @Unique
    private int setupAnimFrameCounter = 0;

    @Override
    public PlayerAnimationManager viaBedrockUtility$getAnimationManager() {
        return this.animationManager;
    }

    @Override
    public void viaBedrockUtility$setAnimationManager(PlayerAnimationManager manager) {
        this.animationManager = manager;
    }

    // Distance-based throttle: for distant players that have Bedrock animations, freeze the WHOLE
    // setupAnim (vanilla pose math + the Bedrock TAIL inject below), keeping the last frame's pose —
    // the standard animation-LOD "freeze, don't interpolate" approach (freezing keeps half the saving
    // that interpolation would give away). Vanilla-only players (no Bedrock animations) always run the
    // full vanilla setupAnim. Near players (LodConfig.shouldAnimate == true) are never frozen.
    //
    // Frozen players still RENDER every frame — ModelPart.render / Cube.compile still runs (now on
    // Sodium's fast path via CuboidMixin) using the frozen pose. This mixin only skips the per-frame
    // pose *computation*: vanilla's limb/head/body math plus the Bedrock MoLang keyframe evaluation,
    // which is the per-player CPU work that scales linearly with a crowded lobby.
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void viaBedrockUtility$throttleDistantPlayer(PlayerRenderState state, CallbackInfo ci) {
        if (this.animationManager == null || !this.animationManager.hasAnimations()) {
            return;
        }
        this.setupAnimFrameCounter++;
        final double distance = Math.sqrt(state.distanceToCameraSq);
        if (!LodConfig.getInstance().shouldAnimate(distance, this.setupAnimFrameCounter)) {
            ci.cancel();
        }
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;)V", at = @At("TAIL"))
    private void applyBedrockAnimations(PlayerRenderState state, CallbackInfo ci) {
        if (this.animationManager == null) {
            return;
        }

        // Clearing of vanilla rotations and applying Bedrock animations is handled
        // per-animation inside PlayerAnimationManager.animate() to avoid clearing
        // bones that are not targeted by a specific animation.
        animationManager.animate((PlayerModel) (Object) this, state);
    }
}
