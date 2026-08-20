package org.oryxel.viabedrockutility.mixin.interfaces;

import net.minecraft.world.item.ItemDisplayContext;
import org.oryxel.viabedrockutility.attachable.AttachableItemSnapshot;

public interface IAttachableItemRenderState {
    void viaBedrockUtility$setAttachableSnapshot(AttachableItemSnapshot snapshot, ItemDisplayContext displayContext);

    AttachableItemSnapshot viaBedrockUtility$getAttachableSnapshot();

    ItemDisplayContext viaBedrockUtility$getAttachableDisplayContext();
}
