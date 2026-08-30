package org.oryxel.viabedrockutility.mixin.interfaces;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import org.oryxel.viabedrockutility.attachable.AttachableItemSnapshot;
import org.oryxel.viabedrockutility.attachable.AttachableOwnerSnapshot;
import org.oryxel.viabedrockutility.animation.PlayerAnimationState;

public interface ICustomPlayerRendererHolder {
    EntityRenderer<?, ?> viaBedrockUtility$getCustomPlayerRenderer();
    void viaBedrockUtility$setCustomPlayerRenderer(EntityRenderer<?, ?> renderer);
    AttachableItemSnapshot viaBedrockUtility$getMainHandSnapshot();
    AttachableItemSnapshot viaBedrockUtility$getOffHandSnapshot();
    void viaBedrockUtility$setHandSnapshots(AttachableItemSnapshot mainHand, AttachableItemSnapshot offHand);
    ResourceLocation viaBedrockUtility$getMainHandItemIdentifier();
    ResourceLocation viaBedrockUtility$getOffHandItemIdentifier();
    void viaBedrockUtility$setHandItemIdentifiers(ResourceLocation mainHand, ResourceLocation offHand);
    AttachableOwnerSnapshot viaBedrockUtility$getOwnerSnapshot();
    void viaBedrockUtility$setOwnerSnapshot(AttachableOwnerSnapshot owner);
    PlayerAnimationState viaBedrockUtility$getPlayerAnimationState();
    void viaBedrockUtility$setPlayerAnimationState(PlayerAnimationState state);
}
