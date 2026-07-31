package org.oryxel.viabedrockutility.mixin.impl.render.item;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.ContextDimension;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.oryxel.viabedrockutility.util.ItemModelDimensionResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ContextDimension.class)
public abstract class ContextDimensionMixin {

    @Inject(
            method = "get(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/entity/LivingEntity;ILnet/minecraft/world/item/ItemDisplayContext;)Lnet/minecraft/resources/ResourceKey;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void normalizeViaBedrockOverworld(
            ItemStack stack,
            ClientLevel level,
            LivingEntity entity,
            int seed,
            ItemDisplayContext displayContext,
            CallbackInfoReturnable<ResourceKey<Level>> cir
    ) {
        ResourceKey<Level> dimension = cir.getReturnValue();
        // Normalize only the dimension exposed to data-driven item model selectors.
        ResourceKey<Level> normalizedDimension = ItemModelDimensionResolver.normalize(dimension);
        if (normalizedDimension != dimension) {
            cir.setReturnValue(normalizedDimension);
        }
    }
}
