package org.oryxel.viabedrockutility.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockEntityMotionTest {
    private static final float EPSILON = 1.0e-6F;

    @Test
    void followsNativeWalkStateAndFrameInterpolation() {
        final BedrockEntityMotion motion = new BedrockEntityMotion();
        motion.tick(0, 0.0, 0.0, 0.0, 0.0F, false);
        motion.tick(1, 0.1, 0.0, 0.0, 0.0F, false);

        // 原生普通实体的参考结果：首次步行状态为 0.16，半帧插值为 0.08。
        assertEquals(2.0F, motion.groundSpeed(), EPSILON);
        assertEquals(0.0F, motion.modifiedMoveSpeed(0.0F, false), EPSILON);
        assertEquals(0.08F, motion.modifiedMoveSpeed(0.5F, false), EPSILON);
        assertEquals(0.16F, motion.modifiedMoveSpeed(1.0F, false), EPSILON);
        assertEquals(0.08F, motion.modifiedDistanceMoved(0.5F), EPSILON);

        motion.tick(2, 0.2, 0.0, 0.0, 0.0F, false);
        assertEquals(0.208F, motion.modifiedMoveSpeed(0.5F, false), EPSILON);
        assertEquals(0.288F, motion.modifiedDistanceMoved(0.5F), EPSILON);

        motion.tick(3, 0.2, 0.0, 0.0, 0.0F, false);
        assertEquals(0.0F, motion.groundSpeed(), EPSILON);
        assertEquals(0.2048F, motion.modifiedMoveSpeed(0.5F, false), EPSILON);
        assertEquals(0.4928F, motion.modifiedDistanceMoved(0.5F), EPSILON);
    }

    @Test
    void repeatedAndReorderedRenderSamplesDoNotAdvanceMovement() {
        final BedrockEntityMotion sparse = new BedrockEntityMotion();
        final BedrockEntityMotion dense = new BedrockEntityMotion();
        for (int tick = 0; tick <= 40; tick++) {
            final double x = tick <= 20 ? tick * 0.1 : 2.0;
            sparse.tick(tick, x, 0.0, 0.0, 0.0F, false);
            dense.tick(tick, x, 0.0, 0.0, 0.0F, false);
            for (int pass = 0; pass < 100; pass++) {
                dense.modifiedMoveSpeed((pass % 7) / 6.0F, false);
                dense.modifiedDistanceMoved((pass % 5) / 4.0F);
            }
            assertEquals(sparse.modifiedMoveSpeed(0.37F, false),
                    dense.modifiedMoveSpeed(0.37F, false), 0.0F);
            assertEquals(sparse.modifiedDistanceMoved(0.37F),
                    dense.modifiedDistanceMoved(0.37F), 0.0F);
        }
    }

    @Test
    void largeMovementCannotAmplifyModifiedSpeedPastNativeLimit() {
        final BedrockEntityMotion motion = new BedrockEntityMotion();
        motion.tick(0, 0.0, 0.0, 0.0, 0.0F, false);
        for (int tick = 1; tick <= 100; tick++) {
            motion.tick(tick, tick * 1000.0, 0.0, 0.0, 0.0F, false);
            assertTrue(motion.modifiedMoveSpeed(0.5F, false) <= 1.0F);
            assertTrue(motion.modifiedMoveSpeed(0.5F, true) <= 1.5F);
        }
        assertEquals(1.0F, motion.modifiedMoveSpeed(1.0F, false), EPSILON);
        assertEquals(1.5F, motion.modifiedMoveSpeed(1.0F, true), EPSILON);
    }

    @Test
    void turningUsesWrappedBodyRotationAndRidingStopsWalkState() {
        final BedrockEntityMotion motion = new BedrockEntityMotion();
        motion.tick(0, 0.0, 0.0, 0.0, 179.0F, false);
        motion.tick(1, 0.0, 0.0, 0.0, -179.0F, false);
        assertEquals(0.04F, motion.modifiedMoveSpeed(1.0F, false), EPSILON);
        final float distance = motion.modifiedDistanceMoved(1.0F);
        motion.tick(2, 1.0, 0.0, 0.0, 0.0F, true);
        assertEquals(0.0F, motion.modifiedMoveSpeed(0.5F, false), EPSILON);
        assertEquals(distance, motion.modifiedDistanceMoved(0.5F), EPSILON);
        assertEquals(20.0F, motion.groundSpeed(), EPSILON);
    }

    @Test
    void physicalQueriesKeepTheirOwnUnitsAndVerticalComponent() {
        final BedrockEntityMotion motion = new BedrockEntityMotion();
        motion.tick(0, 0.0, 0.0, 0.0, 0.0F, false);
        motion.tick(1, 0.0, -0.3, 0.4, 0.0F, false);
        assertEquals(10.0F, motion.groundSpeed(), EPSILON);
        assertEquals(-6.0F, motion.verticalSpeed(), EPSILON);
        assertEquals(0.4F, motion.modifiedMoveSpeed(1.0F, false), EPSILON);
    }

    @Test
    void spawnDuplicateTicksAndRetiredTickSequenceDoNotCreateMotion() {
        final BedrockEntityMotion motion = new BedrockEntityMotion();
        motion.tick(20, 10000.0, 64.0, -10000.0, 0.0F, false);
        motion.tick(20, 10001.0, 64.0, -10000.0, 0.0F, false);
        assertEquals(0.0F, motion.modifiedMoveSpeed(1.0F, false), EPSILON);
        motion.tick(21, 10000.1, 64.0, -10000.0, 0.0F, false);
        assertEquals(0.16F, motion.modifiedMoveSpeed(1.0F, false), EPSILON);
        motion.tick(1, -10000.0, 64.0, 10000.0, 0.0F, false);
        assertEquals(0.0F, motion.modifiedMoveSpeed(1.0F, false), EPSILON);
        assertEquals(0.0F, motion.modifiedDistanceMoved(1.0F), EPSILON);
    }
}
