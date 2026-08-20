package org.oryxel.viabedrockutility.attachable;

import net.minecraft.world.item.ItemDisplayContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachableDisplayContextTest {
    @Test
    void inventoryKeepsIconWhileDetachedWorldViewsUseGeometry() {
        assertFalse(DetachedAttachableRenderer.isDetachedDisplayContext(ItemDisplayContext.GUI));
        assertFalse(DetachedAttachableRenderer.isDetachedDisplayContext(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND));
        assertTrue(DetachedAttachableRenderer.isDetachedDisplayContext(ItemDisplayContext.GROUND));
        assertTrue(DetachedAttachableRenderer.isDetachedDisplayContext(ItemDisplayContext.FIXED));
    }
}
