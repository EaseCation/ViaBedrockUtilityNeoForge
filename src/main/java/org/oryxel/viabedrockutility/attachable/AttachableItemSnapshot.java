package org.oryxel.viabedrockutility.attachable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public record AttachableItemSnapshot(ResourceLocation itemIdentifier, ItemStack stack) {
    public static final AttachableItemSnapshot EMPTY = new AttachableItemSnapshot(null, ItemStack.EMPTY);

    public AttachableItemSnapshot {
        stack = stack == null ? ItemStack.EMPTY : stack.copy();
    }

    public static AttachableItemSnapshot of(ItemStack stack) {
        return of(itemIdentifier(stack), stack);
    }

    static ResourceLocation itemIdentifier(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        final CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            final CompoundTag tag = customData.copyTag();
            if (tag.contains("viabedrock:bedrock_identifier")) {
                return tag.getString("viabedrock:bedrock_identifier")
                        .map(ResourceLocation::tryParse).orElse(null);
            }
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    static AttachableItemSnapshot of(ResourceLocation itemIdentifier, ItemStack stack) {
        return itemIdentifier == null || stack == null || stack.isEmpty()
                ? EMPTY : new AttachableItemSnapshot(itemIdentifier, stack);
    }

    public boolean isEmpty() {
        return itemIdentifier == null || stack.isEmpty();
    }
}
