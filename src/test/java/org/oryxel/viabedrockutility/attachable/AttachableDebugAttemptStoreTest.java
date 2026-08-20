package org.oryxel.viabedrockutility.attachable;

import org.junit.jupiter.api.Test;
import org.oryxel.viabedrockutility.attachable.AttachableDebugLog.AttemptStage;
import org.oryxel.viabedrockutility.attachable.AttachableDebugLog.DebugAttempt;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AttachableDebugAttemptStoreTest {
    @Test
    void refreshesLiveAttemptsAndEvictsRetiredOwners() {
        final AttachableDebugAttemptStore store = new AttachableDebugAttemptStore();
        final var liveKey = new AttachableRuntimeRegistry.RuntimeKey(
                UUID.randomUUID(), AttachableQueryContext.LogicalHand.MAIN_HAND);
        final var retiredKey = new AttachableRuntimeRegistry.RuntimeKey(
                UUID.randomUUID(), AttachableQueryContext.LogicalHand.OFF_HAND);
        final DebugAttempt live = attempt(liveKey);
        final DebugAttempt retired = attempt(retiredKey);

        assertNull(store.record(live, 10L));
        assertNull(store.record(retired, 10L));
        assertEquals(live, store.record(live, 25L));

        store.evictOlderThan(20L);

        assertEquals(List.of(live), store.snapshot());
    }

    private static DebugAttempt attempt(AttachableRuntimeRegistry.RuntimeKey key) {
        return new DebugAttempt(key, 1L, "minecraft:stick",
                AttachableQueryContext.ViewContext.THIRD_PERSON, AttemptStage.NO_CANDIDATES,
                0, "", List.of(), "", "test");
    }
}
