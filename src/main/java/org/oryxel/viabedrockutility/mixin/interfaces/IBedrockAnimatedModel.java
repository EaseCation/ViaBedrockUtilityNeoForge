package org.oryxel.viabedrockutility.mixin.interfaces;

import org.oryxel.viabedrockutility.animation.PlayerAnimationManager;

public interface IBedrockAnimatedModel {
    PlayerAnimationManager viaBedrockUtility$getAnimationManager();
    void viaBedrockUtility$setAnimationManager(PlayerAnimationManager manager);
}
