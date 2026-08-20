package org.oryxel.viabedrockutility.mixin.impl.render.item;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.oryxel.viabedrockutility.attachable.AttachableItemSnapshot;
import org.oryxel.viabedrockutility.mixin.interfaces.IAttachableItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @Inject(method = "updateForTopItem", at = @At("TAIL"))
    private void captureAttachableItem(ItemStackRenderState state, ItemStack stack,
                                       ItemDisplayContext displayContext, Level level,
                                       LivingEntity livingEntity, int seed, CallbackInfo ci) {
        ((IAttachableItemRenderState) state).viaBedrockUtility$setAttachableSnapshot(
                AttachableItemSnapshot.of(stack), displayContext);
    }
}
