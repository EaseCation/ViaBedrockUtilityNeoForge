package org.oryxel.viabedrockutility.payload;

import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;
import org.oryxel.viabedrockutility.entity.CustomEntityTicker;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomEntityHurtLifecycleTest {
    @Test
    void damageTargetsOnlyCachedCustomModels() {
        final PayloadHandler handler = new PayloadHandler();
        final UUID custom = UUID.randomUUID();
        final UUID ordinary = UUID.randomUUID();
        handler.getCachedCustomEntities().put(custom, new ObjenesisStd().newInstance(CustomEntityTicker.class));
        handler.markCustomEntityHurt(custom, 100L);
        handler.markCustomEntityHurt(ordinary, 100L);
        assertTrue(handler.getCustomEntityHurtTracker().isHurt(custom, 100L));
        assertFalse(handler.getCustomEntityHurtTracker().isHurt(ordinary, 100L));
    }

    @Test
    void removalAndResetClearHurtEvenWithoutACachedRenderer() {
        final PayloadHandler handler = new PayloadHandler();
        final UUID removed = UUID.randomUUID();
        final UUID remaining = UUID.randomUUID();
        handler.getCustomEntityHurtTracker().markHurt(removed, 100L);
        handler.getCustomEntityHurtTracker().markHurt(remaining, 100L);
        handler.removeCustomEntity(removed);
        assertFalse(handler.getCustomEntityHurtTracker().isHurt(removed, 100L));
        assertTrue(handler.getCustomEntityHurtTracker().isHurt(remaining, 100L));
        handler.resetConnectionState();
        assertFalse(handler.getCustomEntityHurtTracker().isHurt(remaining, 100L));
    }
}
