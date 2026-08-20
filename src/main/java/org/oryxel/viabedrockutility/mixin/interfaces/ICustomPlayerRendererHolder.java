package org.oryxel.viabedrockutility.mixin.interfaces;

import net.minecraft.client.renderer.entity.EntityRenderer;
import org.oryxel.viabedrockutility.attachable.AttachableItemSnapshot;
import org.oryxel.viabedrockutility.attachable.AttachableOwnerSnapshot;

public interface ICustomPlayerRendererHolder {
    EntityRenderer<?, ?> viaBedrockUtility$getCustomPlayerRenderer();
    void viaBedrockUtility$setCustomPlayerRenderer(EntityRenderer<?, ?> renderer);
    AttachableItemSnapshot viaBedrockUtility$getMainHandSnapshot();
    AttachableItemSnapshot viaBedrockUtility$getOffHandSnapshot();
    void viaBedrockUtility$setHandSnapshots(AttachableItemSnapshot mainHand, AttachableItemSnapshot offHand);
    AttachableOwnerSnapshot viaBedrockUtility$getOwnerSnapshot();
    void viaBedrockUtility$setOwnerSnapshot(AttachableOwnerSnapshot owner);
}
