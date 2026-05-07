package org.oryxel.viabedrockutility.mixin.impl.render;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.oryxel.viabedrockutility.animation.PlayerAnimationManager;
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

    @Override
    public PlayerAnimationManager viaBedrockUtility$getAnimationManager() {
        return this.animationManager;
    }

    @Override
    public void viaBedrockUtility$setAnimationManager(PlayerAnimationManager manager) {
        this.animationManager = manager;
    }

    @Inject(method = "setAngles(Lnet.minecraft.client.renderer.entity.state.PlayerRenderState;)V", at = @At("TAIL"))
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
