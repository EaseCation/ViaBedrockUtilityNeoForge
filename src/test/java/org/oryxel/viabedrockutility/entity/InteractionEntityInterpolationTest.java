package org.oryxel.viabedrockutility.entity;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler;
import org.oryxel.viabedrockutility.payload.impl.entity.CustomEntityMotionPayload;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InteractionEntityInterpolationTest {
    private final ViaBedrockUtility utility = ViaBedrockUtility.getInstance();
    private final CustomEntityPayloadHandler handler = new CustomEntityPayloadHandler();
    private final BedrockEntityMotion motion = new BedrockEntityMotion();
    private Field handlerField;
    private Object previousHandler;
    private boolean previousPresence;
    private ProbeInteraction entity;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void bindCustomEntity() throws Exception {
        handlerField = ViaBedrockUtility.class.getDeclaredField("payloadHandler");
        handlerField.setAccessible(true);
        previousHandler = handlerField.get(utility);
        previousPresence = utility.isViaBedrockPresent();
        handlerField.set(utility, handler);
        utility.setViaBedrockPresent(true);
        final Level level = mock(Level.class);
        when(level.noCollision(any(Entity.class), any(AABB.class))).thenReturn(true);
        entity = new ProbeInteraction(level);
        final CustomEntityTicker ticker = mock(CustomEntityTicker.class);
        when(ticker.getMotion()).thenReturn(motion);
        handler.getCachedCustomEntities().put(entity.getUUID(), ticker);
        entity.snapTo(0, 0, 0, 0, 0);
    }

    @AfterEach
    void restoreConnection() throws Exception {
        handlerField.set(utility, previousHandler);
        utility.setViaBedrockPresent(previousPresence);
    }

    @Test
    void oldProxyAndUnregisteredInteractionsKeepImmediateMovement() {
        assertNull(entity.getInterpolation());
        entity.moveOrInterpolateTo(new Vec3(3, 0, 0), 90, 0);
        assertEquals(3, entity.getX());
        handler.handle(CustomEntityMotionPayload.enable());
        handler.getCachedCustomEntities().clear();
        assertNull(entity.getInterpolation());
        entity.moveOrInterpolateTo(new Vec3(6, 0, 0), 180, 0);
        assertEquals(6, entity.getX());
    }

    @Test
    void nativePositionUpdateMovesEntityAndHitboxTogetherInThreeTicks() {
        handler.handle(CustomEntityMotionPayload.enable());
        entity.moveOrInterpolateTo(new Vec3(3, 0, 0), 90, 0);
        assertEquals(0, entity.getX());
        for (int tick = 1; tick <= 3; tick++) {
            advance();
            assertEquals(tick, entity.getX(), 1.0e-6);
            assertEquals(entity.getX(), entity.getBoundingBox().getCenter().x, 1.0e-6);
        }
        assertEquals(90, entity.getYRot(), 1.0e-6);
        assertFalse(entity.isInterpolating());
        advance();
        assertEquals(3, entity.getX(), 1.0e-6);
    }

    @Test
    void renderSamplesDoNotAdvanceMovementAndNewPacketsReplaceTheTarget() {
        handler.handle(CustomEntityMotionPayload.enable());
        entity.moveOrInterpolateTo(new Vec3(3, 0, 0), 0, 0);
        advance();
        for (int frame = 0; frame < 100; frame++) {
            entity.getPosition((frame % 10) / 10.0F);
            entity.getInterpolation();
        }
        assertEquals(1, entity.getX(), 1.0e-6);
        entity.moveOrInterpolateTo(new Vec3(4, 0, 0), 0, 0);
        advance();
        assertEquals(2, entity.getX(), 1.0e-6);
        advance();
        advance();
        assertEquals(4, entity.getX(), 1.0e-6);
    }

    @Test
    void shortTeleportCancelsTheOldTargetAndDoesNotBecomeWalkDistance() {
        handler.handle(CustomEntityMotionPayload.enable());
        entity.moveOrInterpolateTo(new Vec3(3, 0, 0), 0, 0);
        advance();
        final float walked = motion.modifiedDistanceMoved(1);
        entity.snapTo(2, 0, 0, 179, 0);
        assertFalse(entity.isInterpolating());
        advance();
        assertEquals(2, entity.getX(), 1.0e-6);
        assertEquals(0, motion.groundSpeed(), 1.0e-6);
        assertEquals(walked, motion.modifiedDistanceMoved(1), 1.0e-6);
        entity.moveOrInterpolateTo(new Vec3(2, 0, 0), -179, 0);
        advance();
        assertTrue(Math.abs(entity.getYRot() - 179) < 1);
    }

    @Test
    void directCorrectionsRidingAndDeregisteringCannotResumeAnOldTarget() {
        handler.handle(CustomEntityMotionPayload.enable());
        entity.moveOrInterpolateTo(new Vec3(9, 0, 0), 0, 0);
        entity.setPos(2, 0, 0);
        advance();
        assertEquals(2, entity.getX());
        entity.moveOrInterpolateTo(new Vec3(9, 0, 0), 0, 0);
        entity.riding = true;
        advance();
        entity.riding = false;
        advance();
        assertEquals(2, entity.getX());
        entity.moveOrInterpolateTo(new Vec3(9, 0, 0), 0, 0);
        handler.getCachedCustomEntities().clear();
        advance();
        assertEquals(2, entity.getX());
    }

    private void advance() {
        entity.setOldPosAndRot();
        entity.tickCount++;
        entity.tick();
        motion.tick(entity.tickCount, entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.isPassenger());
    }

    private static final class ProbeInteraction extends Interaction {
        private boolean riding;

        private ProbeInteraction(Level level) {
            super(EntityType.INTERACTION, level);
        }

        @Override
        public boolean isPassenger() {
            return riding;
        }
    }
}
