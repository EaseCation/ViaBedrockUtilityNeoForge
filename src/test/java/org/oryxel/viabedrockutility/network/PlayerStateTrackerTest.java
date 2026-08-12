package org.oryxel.viabedrockutility.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStateTrackerTest {

    @Test
    void sendsInitialStateAndOnlySubsequentEdges() {
        final PlayerStateTracker tracker = new PlayerStateTracker();
        final Object player = new Object();
        final int crawling = PlayerStateFlags.CRAWLING;

        assertNull(tracker.stateToSend(player, false, 0));
        assertEquals(0, tracker.stateToSend(player, true, 0));
        tracker.markSent(player, 0);
        assertNull(tracker.stateToSend(player, true, 0));

        assertEquals(crawling, tracker.stateToSend(player, true, crawling));
        tracker.markSent(player, crawling);
        assertNull(tracker.stateToSend(player, true, crawling));

        assertEquals(0, tracker.stateToSend(player, true, 0));
        tracker.markSent(player, 0);
        assertNull(tracker.stateToSend(player, true, 0));
    }

    @Test
    void sendsChangesToStatesThatViaBedrockDoesNotYetConsume() {
        final PlayerStateTracker tracker = new PlayerStateTracker();
        final Object player = new Object();
        final int initialFlags = PlayerStateFlags.CRAWLING | PlayerStateFlags.SNEAKING;
        final int changedFlags = initialFlags | PlayerStateFlags.SPRINTING;

        assertEquals(initialFlags, tracker.stateToSend(player, true, initialFlags));
        tracker.markSent(player, initialFlags);
        assertEquals(changedFlags, tracker.stateToSend(player, true, changedFlags));
    }

    @Test
    void resendsStateForAReplacementPlayerOrAfterReset() {
        final PlayerStateTracker tracker = new PlayerStateTracker();
        final Object firstPlayer = new Object();
        final Object secondPlayer = new Object();

        assertEquals(PlayerStateFlags.CRAWLING,
                tracker.stateToSend(firstPlayer, true, PlayerStateFlags.CRAWLING));
        tracker.markSent(firstPlayer, PlayerStateFlags.CRAWLING);
        assertEquals(PlayerStateFlags.CRAWLING,
                tracker.stateToSend(secondPlayer, true, PlayerStateFlags.CRAWLING));
        tracker.markSent(secondPlayer, PlayerStateFlags.CRAWLING);

        tracker.reset();
        assertEquals(PlayerStateFlags.CRAWLING,
                tracker.stateToSend(secondPlayer, true, PlayerStateFlags.CRAWLING));
    }

    @Test
    void buildsAllSupportedStateFlags() {
        assertEquals((1 << 6) - 1, PlayerStateFlags.create(true, true, true, true, true, true));
        assertEquals(PlayerStateFlags.SNEAKING | PlayerStateFlags.FLYING,
                PlayerStateFlags.create(false, true, false, false, false, true));
    }

    @Test
    void distinguishesCrawlingFromSwimming() {
        assertTrue(PlayerStateFlags.isCrawling(true, false, false));
        assertFalse(PlayerStateFlags.isCrawling(true, true, false));
        assertFalse(PlayerStateFlags.isCrawling(true, false, true));
        assertFalse(PlayerStateFlags.isCrawling(false, false, false));
    }
}
