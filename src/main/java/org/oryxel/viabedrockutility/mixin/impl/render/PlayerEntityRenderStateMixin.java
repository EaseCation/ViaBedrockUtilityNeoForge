package org.oryxel.viabedrockutility.mixin.impl.render;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.resources.ResourceLocation;
import org.oryxel.viabedrockutility.mixin.interfaces.ICustomPlayerRendererHolder;
import org.oryxel.viabedrockutility.attachable.AttachableItemSnapshot;
import org.oryxel.viabedrockutility.attachable.AttachableOwnerSnapshot;
import org.oryxel.viabedrockutility.animation.PlayerAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerRenderState.class)
public abstract class PlayerEntityRenderStateMixin implements ICustomPlayerRendererHolder {
    @Unique
    private EntityRenderer<?, ?> customPlayerRenderer;
    @Unique
    private AttachableItemSnapshot viabedrockutility$mainHand = AttachableItemSnapshot.EMPTY;
    @Unique
    private AttachableItemSnapshot viabedrockutility$offHand = AttachableItemSnapshot.EMPTY;
    @Unique
    private ResourceLocation viabedrockutility$mainHandItemIdentifier;
    @Unique
    private ResourceLocation viabedrockutility$offHandItemIdentifier;
    @Unique
    private AttachableOwnerSnapshot viabedrockutility$owner = AttachableOwnerSnapshot.EMPTY;
    @Unique
    private PlayerAnimationState viabedrockutility$playerAnimationState;

    @Override
    public EntityRenderer<?, ?> viaBedrockUtility$getCustomPlayerRenderer() {
        return this.customPlayerRenderer;
    }

    @Override
    public void viaBedrockUtility$setCustomPlayerRenderer(EntityRenderer<?, ?> renderer) {
        this.customPlayerRenderer = renderer;
    }

    @Override
    public AttachableItemSnapshot viaBedrockUtility$getMainHandSnapshot() {
        return viabedrockutility$mainHand;
    }

    @Override
    public AttachableItemSnapshot viaBedrockUtility$getOffHandSnapshot() {
        return viabedrockutility$offHand;
    }

    @Override
    public void viaBedrockUtility$setHandSnapshots(AttachableItemSnapshot mainHand, AttachableItemSnapshot offHand) {
        viabedrockutility$mainHand = mainHand;
        viabedrockutility$offHand = offHand;
    }

    @Override
    public ResourceLocation viaBedrockUtility$getMainHandItemIdentifier() {
        return viabedrockutility$mainHandItemIdentifier;
    }

    @Override
    public ResourceLocation viaBedrockUtility$getOffHandItemIdentifier() {
        return viabedrockutility$offHandItemIdentifier;
    }

    @Override
    public void viaBedrockUtility$setHandItemIdentifiers(ResourceLocation mainHand, ResourceLocation offHand) {
        viabedrockutility$mainHandItemIdentifier = mainHand;
        viabedrockutility$offHandItemIdentifier = offHand;
    }

    @Override
    public AttachableOwnerSnapshot viaBedrockUtility$getOwnerSnapshot() {
        return viabedrockutility$owner;
    }

    @Override
    public void viaBedrockUtility$setOwnerSnapshot(AttachableOwnerSnapshot owner) {
        viabedrockutility$owner = owner;
    }

    @Override
    public PlayerAnimationState viaBedrockUtility$getPlayerAnimationState() {
        return viabedrockutility$playerAnimationState;
    }

    @Override
    public void viaBedrockUtility$setPlayerAnimationState(PlayerAnimationState state) {
        viabedrockutility$playerAnimationState = state;
    }
}
