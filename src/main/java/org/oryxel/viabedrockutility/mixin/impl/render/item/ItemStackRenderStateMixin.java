package org.oryxel.viabedrockutility.mixin.impl.render.item;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.attachable.AttachableItemSnapshot;
import org.oryxel.viabedrockutility.mixin.interfaces.IAttachableItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateMixin implements IAttachableItemRenderState {
    @Unique
    private AttachableItemSnapshot viabedrockutility$attachableSnapshot = AttachableItemSnapshot.EMPTY;
    @Unique
    private ItemDisplayContext viabedrockutility$attachableDisplayContext = ItemDisplayContext.NONE;

    @Override
    public void viaBedrockUtility$setAttachableSnapshot(AttachableItemSnapshot snapshot,
                                                        ItemDisplayContext displayContext) {
        viabedrockutility$attachableSnapshot = snapshot == null ? AttachableItemSnapshot.EMPTY : snapshot;
        viabedrockutility$attachableDisplayContext = displayContext == null
                ? ItemDisplayContext.NONE : displayContext;
    }

    @Override
    public AttachableItemSnapshot viaBedrockUtility$getAttachableSnapshot() {
        return viabedrockutility$attachableSnapshot;
    }

    @Override
    public ItemDisplayContext viaBedrockUtility$getAttachableDisplayContext() {
        return viabedrockutility$attachableDisplayContext;
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void clearAttachableSnapshot(CallbackInfo ci) {
        viabedrockutility$attachableSnapshot = AttachableItemSnapshot.EMPTY;
        viabedrockutility$attachableDisplayContext = ItemDisplayContext.NONE;
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void renderDetachedAttachable(PoseStack poses, MultiBufferSource buffers,
                                          int packedLight, int packedOverlay, CallbackInfo ci) {
        if (ViaBedrockUtility.getInstance().getAttachableRuntimeManager().renderDetached(
                viabedrockutility$attachableSnapshot, viabedrockutility$attachableDisplayContext,
                poses, buffers, packedLight, packedOverlay)) {
            ci.cancel();
        }
    }
}
