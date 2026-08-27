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
        final DebugAttempt live = attempt(liveKey, 10L);
        final DebugAttempt refreshedLive = attempt(liveKey, 25L);
        final DebugAttempt retired = attempt(retiredKey, 10L);

        assertNull(store.record(live, 10L));
        assertNull(store.record(retired, 10L));
        assertEquals(live, store.record(refreshedLive, 25L));

        store.evictOlderThan(20L);

        assertEquals(List.of(refreshedLive), store.snapshot());
    }

    private static DebugAttempt attempt(AttachableRuntimeRegistry.RuntimeKey key, long tick) {
        return new DebugAttempt(key, 1L, tick, "minecraft:stick",
                AttachableQueryContext.ViewContext.THIRD_PERSON, AttemptStage.NO_CANDIDATES,
                0, "", List.of(), "", "test");
    }
}
