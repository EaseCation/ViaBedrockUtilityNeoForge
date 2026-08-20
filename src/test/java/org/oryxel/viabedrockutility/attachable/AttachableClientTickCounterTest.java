package org.oryxel.viabedrockutility.attachable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachableClientTickCounterTest {
    @Test
    void ticksAreMonotonicAndResetOnlyWhenLevelIdentityChanges() {
        var counter = new AttachableClientTickCounter();
        var firstLevel = new Object();
        var secondLevel = new Object();

        var initial = counter.synchronize(firstLevel);
        assertTrue(initial.levelChanged());
        assertEquals(0L, initial.tick());

        assertEquals(1L, counter.advance(firstLevel).tick());
        assertEquals(2L, counter.advance(firstLevel).tick());
        var renderSample = counter.synchronize(firstLevel);
        assertFalse(renderSample.levelChanged());
        assertEquals(2L, renderSample.tick());

        var switched = counter.synchronize(secondLevel);
        assertTrue(switched.levelChanged());
        assertEquals(0L, switched.tick());
        assertEquals(1L, counter.advance(secondLevel).tick());

        var disconnected = counter.synchronize(null);
        assertTrue(disconnected.levelChanged());
        assertEquals(0L, disconnected.tick());
    }
}
