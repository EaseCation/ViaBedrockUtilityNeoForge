package org.oryxel.viabedrockutility.attachable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public record AttachableQueryContext(UUID ownerUuid, Entity owner, ItemStack itemStackSnapshot,
                                     LogicalHand hand, HumanoidArm arm, ViewContext viewContext,
                                     long tick, float partialTick, String attachableIdentifier) {
    public AttachableQueryContext {
        itemStackSnapshot = itemStackSnapshot == null ? ItemStack.EMPTY : itemStackSnapshot.copy();
    }

    public enum ViewContext {
        FIRST_PERSON, THIRD_PERSON, DETACHED
    }

    public enum LogicalHand {
        MAIN_HAND, OFF_HAND
    }
}
