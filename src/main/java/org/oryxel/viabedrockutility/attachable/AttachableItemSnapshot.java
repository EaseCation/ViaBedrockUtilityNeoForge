package org.oryxel.viabedrockutility.attachable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record AttachableItemSnapshot(ResourceLocation itemIdentifier, ItemStack stack) {
    public static final AttachableItemSnapshot EMPTY = new AttachableItemSnapshot(null, ItemStack.EMPTY);

    public AttachableItemSnapshot {
        stack = stack == null ? ItemStack.EMPTY : stack.copy();
    }

    public static AttachableItemSnapshot of(ItemStack stack) {
        return stack == null || stack.isEmpty()
                ? EMPTY
                : new AttachableItemSnapshot(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack);
    }

    public boolean isEmpty() {
        return itemIdentifier == null || stack.isEmpty();
    }
}
