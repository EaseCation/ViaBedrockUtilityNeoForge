package org.oryxel.viabedrockutility.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomEntityHurtTrackerTest {
    @Test
    void localTicksExpireEvenWhenServerWorldTimeIsResetOrFrozen() {
        final CustomEntityHurtTracker tracker = new CustomEntityHurtTracker();
        final Object level = new Object();
        final UUID uuid = UUID.randomUUID();
        tracker.markHurt(uuid, tracker.currentTick(level));
        for (int localTick = 0; localTick < 10; localTick++) {
            assertTrue(tracker.isHurt(uuid, tracker.currentTick(level)));
            // 服务器时间可在任意渲染帧被同步；读取状态不推进本地计时。
            for (int frame = 0; frame < 10; frame++) {
                assertEquals(localTick, tracker.currentTick(level));
            }
            tracker.advanceTick(level, false);
        }
        assertFalse(tracker.isHurt(uuid, tracker.currentTick(level)));
    }

    @Test
    void pausedTicksAndWorldReplacementCannotLeakHurt() {
        final CustomEntityHurtTracker tracker = new CustomEntityHurtTracker();
        final Object firstLevel = new Object();
        final Object secondLevel = new Object();
        final UUID uuid = UUID.randomUUID();
        tracker.markHurt(uuid, tracker.currentTick(firstLevel));
        for (int tick = 0; tick < 100; tick++) {
            tracker.advanceTick(firstLevel, true);
        }
        assertTrue(tracker.isHurt(uuid, tracker.currentTick(firstLevel)));
        assertFalse(tracker.isHurt(uuid, tracker.currentTick(secondLevel)));
        tracker.markHurt(uuid, tracker.currentTick(secondLevel));
        tracker.advanceTick(null, false);
        assertFalse(tracker.isHurt(uuid, tracker.currentTick(secondLevel)));
    }

    @Test
    void expiresAfterTenWorldTicksWithoutRenderDrivenCountdown() {
        final CustomEntityHurtTracker tracker = new CustomEntityHurtTracker();
        final UUID uuid = UUID.randomUUID();
        assertFalse(tracker.isHurt(uuid, 100L));
        tracker.markHurt(uuid, 100L);
        for (int frame = 0; frame < 100; frame++) {
            assertTrue(tracker.isHurt(uuid, 100L));
        }
        assertTrue(tracker.isHurt(uuid, 109L));
        assertFalse(tracker.isHurt(uuid, 110L));
        assertFalse(tracker.isHurt(uuid, 111L));
    }

    @Test
    void repeatedDamageRestartsOnlyTheTargetWindow() {
        final CustomEntityHurtTracker tracker = new CustomEntityHurtTracker();
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();
        tracker.markHurt(first, 100L);
        tracker.markHurt(second, 100L);
        tracker.markHurt(first, 100L);
        tracker.markHurt(first, 105L);
        assertFalse(tracker.isHurt(second, 110L));
        assertTrue(tracker.isHurt(first, 114L));
        assertFalse(tracker.isHurt(first, 115L));
    }

    @Test
    void removalAndConnectionResetDoNotLeakToReusedIdentity() {
        final CustomEntityHurtTracker tracker = new CustomEntityHurtTracker();
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();
        tracker.markHurt(first, 100L);
        tracker.markHurt(second, 100L);
        tracker.clear(first);
        assertFalse(tracker.isHurt(first, 101L));
        assertTrue(tracker.isHurt(second, 101L));
        tracker.clearAll();
        assertFalse(tracker.isHurt(second, 101L));
        tracker.markHurt(first, 200L);
        assertTrue(tracker.isHurt(first, 200L));
    }

    @Test
    void pruningUnrenderedEntitiesAlsoClearsRewoundWorldTime() {
        final CustomEntityHurtTracker tracker = new CustomEntityHurtTracker();
        final UUID uuid = UUID.randomUUID();
        tracker.markHurt(uuid, 100L);
        tracker.prune(110L);
        assertFalse(tracker.isHurt(uuid, 105L));
        tracker.markHurt(uuid, 200L);
        tracker.prune(1L);
        assertFalse(tracker.isHurt(uuid, 201L));
    }
}
