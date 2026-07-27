package org.oryxel.viabedrockutility.renderer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrozenMeshAnimationPolicyTest {
    @Test
    void zeroDurationAnimationReleasesAfterOneRenderedFrame() {
        assertFalse(FrozenMeshAnimationPolicy.shouldReleaseExplicit(
                false, true, 0, false, false, true));
        assertTrue(FrozenMeshAnimationPolicy.shouldReleaseExplicit(
                false, true, 1, false, false, true));
    }

    @Test
    void loopingAnimationFinishesOneCycleOnlyAtFrozenDistance() {
        assertFalse(FrozenMeshAnimationPolicy.shouldReleaseExplicit(
                true, false, 1, true, false, false));
        assertTrue(FrozenMeshAnimationPolicy.shouldReleaseExplicit(
                true, false, 1, true, false, true));
    }

    @Test
    void timeoutCannotLeaveAFarAnimationPermanentlyDynamic() {
        assertTrue(FrozenMeshAnimationPolicy.shouldReleaseExplicit(
                false, false, 1, false, true, false));
        assertTrue(FrozenMeshAnimationPolicy.shouldReleaseExplicit(
                true, false, 1, false, true, true));
        assertFalse(FrozenMeshAnimationPolicy.shouldReleaseExplicit(
                true, false, 1, false, true, false));
    }

    @Test
    void queryRebakeWaitsForTheStabilityDeadline() {
        long deadline = 1_000L;
        assertTrue(FrozenMeshAnimationPolicy.queryRebakePending(deadline, 999L));
        assertFalse(FrozenMeshAnimationPolicy.queryRebakePending(deadline, 1_000L));
        assertFalse(FrozenMeshAnimationPolicy.queryRebakePending(0L, 999L));
    }
}
