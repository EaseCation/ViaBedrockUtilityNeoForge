package org.oryxel.viabedrockutility.mixin.impl.render;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.oryxel.viabedrockutility.animation.PlayerAnimationManager;
import org.oryxel.viabedrockutility.config.LodConfig;
import org.oryxel.viabedrockutility.mixin.interfaces.IBedrockAnimatedModel;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;
import org.oryxel.viabedrockutility.renderer.AnimationBudget;
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
    // frameCounter % interval). Incremented on every setupAnim that isn't short-circuited. Seeded with
    // identityHashCode so that different players' throttled updates land on different frames (staggered),
    // mirroring CustomEntityRenderer.renderFrameCounter — otherwise every budget-throttled player would
    // animate on the same frame%interval==0 tick, producing a periodic spike.
    @Unique
    private int setupAnimFrameCounter = System.identityHashCode(this);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void viaBedrockUtility$nameVanillaPlayerBones(ModelPart root, boolean slim, CallbackInfo ci) {
        final PlayerModel model = (PlayerModel) (Object) this;
        viaBedrockUtility$nameVanillaBone(model.head, "head");
        viaBedrockUtility$nameVanillaBone(model.body, "body");
        viaBedrockUtility$nameVanillaBone(model.rightArm, "rightarm");
        viaBedrockUtility$nameVanillaBone(model.leftArm, "leftarm");
        viaBedrockUtility$nameVanillaBone(model.rightLeg, "rightleg");
        viaBedrockUtility$nameVanillaBone(model.leftLeg, "leftleg");
    }

    @Unique
    private static void viaBedrockUtility$nameVanillaBone(ModelPart part, String name) {
        final IModelPart extension = (IModelPart) (Object) part;
        if (extension.viaBedrockUtility$getName() == null
                || extension.viaBedrockUtility$getName().isEmpty()) {
            extension.viaBedrockUtility$setName(name);
            extension.viaBedrockUtility$setVBUModel();
        }
    }

    @Override
    public PlayerAnimationManager viaBedrockUtility$getAnimationManager() {
        return this.animationManager;
    }

    @Override
    public void viaBedrockUtility$setAnimationManager(PlayerAnimationManager manager) {
        this.animationManager = manager;
    }

    // Throttle for players that have Bedrock animations. When a player should not animate this frame, the
    // WHOLE setupAnim is frozen (vanilla pose math + the Bedrock TAIL inject below), keeping the last
    // frame's pose — the standard animation-LOD "freeze, don't interpolate" approach. Vanilla-only players
    // (no Bedrock animations) always run the full vanilla setupAnim.
    //
    // Three stages, mirroring CustomEntityRenderer for the custom-entity pool:
    //   1. Distance LOD (LodConfig.shouldAnimate): far players throttle by tier cadence; near players pass.
    //   2. Player budget (AnimationBudget.tryAcquirePlayer): near players aren't distance-throttled, so a
    //      crowded lobby would animate every one every frame. The player-INDEPENDENT budget caps how many
    //      run full-rate; it does not compete with the custom-entity pool.
    //   3. Budget fallback: players that exhaust the budget drop to a staggered every-N-frames cadence
    //      (animationThrottleInterval), spread across frames by the identityHashCode-seeded counter.
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

        // Stage 1: distance LOD.
        boolean shouldAnimate = LodConfig.getInstance().shouldAnimate(distance, this.setupAnimFrameCounter);

        // Stage 2 + 3: player budget, with staggered every-N-frames fallback when exhausted.
        if (shouldAnimate && !AnimationBudget.tryAcquirePlayer()) {
            final int interval = LodConfig.getInstance().getAnimationThrottleInterval();
            shouldAnimate = interval <= 1 || (this.setupAnimFrameCounter % interval == 0);
        }

        if (!shouldAnimate) {
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
