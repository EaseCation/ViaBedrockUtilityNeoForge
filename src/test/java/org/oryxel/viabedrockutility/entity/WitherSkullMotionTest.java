package org.oryxel.viabedrockutility.entity;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.WitherSkull;
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

class WitherSkullMotionTest {
    private final ViaBedrockUtility utility = ViaBedrockUtility.getInstance();
    private final CustomEntityPayloadHandler handler = new CustomEntityPayloadHandler();
    private Field handlerField;
    private Object previousHandler;
    private boolean previousPresence;
    private ProbeSkull skull;
    private Level level;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void bindConnection() throws Exception {
        handlerField = ViaBedrockUtility.class.getDeclaredField("payloadHandler");
        handlerField.setAccessible(true);
        previousHandler = handlerField.get(utility);
        previousPresence = utility.isViaBedrockPresent();
        handlerField.set(utility, handler);
        utility.setViaBedrockPresent(true);
        level = mock(Level.class);
        when(level.noCollision(any(Entity.class), any(AABB.class))).thenReturn(true);
        skull = new ProbeSkull(level);
        skull.snapTo(0, 64, 0, 0, 0);
    }

    @AfterEach
    void restoreConnection() throws Exception {
        handlerField.set(utility, previousHandler);
        utility.setViaBedrockPresent(previousPresence);
    }

    @Test
    void unnegotiatedConnectionsAndOtherFireballsKeepVanillaMotion() {
        assertNull(skull.getInterpolation());
        handler.handle(CustomEntityMotionPayload.enable());
        assertNull(skull.getInterpolation());
        handler.handle(CustomEntityMotionPayload.enableWitherSkulls());
        assertNotNull(skull.getInterpolation());
        assertNull(new LargeFireball(EntityType.FIREBALL, level).getInterpolation());
        utility.setViaBedrockPresent(false);
        assertNull(skull.getInterpolation());
    }

    @Test
    void normalAndDangerousSkullsFollowBedrockDragWithoutJavaAcceleration() {
        handler.handle(CustomEntityMotionPayload.enableWitherSkulls());
        for (boolean dangerous : new boolean[]{false, true}) {
            skull.setDangerous(dangerous);
            skull.snapTo(0, 64, 0, 0, 0);
            double x = 0;
            double speed = dangerous ? 0.15 : 0.3;
            skull.accelerationPower = 0.1;
            skull.setDeltaMovement(speed, 0.02, 0);
            for (int tick = 1; tick <= 100; tick++) {
                speed *= 0.99;
                x += speed;
                final double y = 64 + tick * 0.02;
                skull.moveOrInterpolateTo(new Vec3(x, y, 0), 90, 0);
                advance();
                assertEquals(x, skull.getX(), 1.0e-6);
                assertEquals(y, skull.getY(), 1.0e-6);
                assertEquals(skull.getX(), skull.getBoundingBox().getCenter().x, 1.0e-6);
            }
            final Vec3 stopped = skull.position();
            for (int tick = 0; tick < 10; tick++) advance();
            assertEquals(stopped, skull.position());
        }
    }

    @Test
    void reflectionStopsAndBurstyUpdatesUseOnlyTheNewestAuthoritativePosition() {
        handler.handle(CustomEntityMotionPayload.enableWitherSkulls());
        skull.moveOrInterpolateTo(new Vec3(0.3, 64, 0), 90, 0);
        skull.moveOrInterpolateTo(new Vec3(0.6, 64, 0), 90, 0);
        advance();
        assertEquals(0.6, skull.getX(), 1.0e-6);
        skull.setDeltaMovement(-0.3, 0, 0);
        skull.moveOrInterpolateTo(new Vec3(0.3, 64, 0), -90, 0);
        advance();
        assertEquals(0.3, skull.getX(), 1.0e-6);
        assertEquals(-90, skull.getYRot(), 1.0e-6);
        skull.setDeltaMovement(Vec3.ZERO);
        advance();
        assertEquals(0.3, skull.getX(), 1.0e-6);
        for (int frame = 0; frame < 100; frame++) skull.getPosition(frame / 100.0F);
        assertEquals(0.3, skull.getX(), 1.0e-6);
    }

    @Test
    void teleportAndConnectionResetCannotResumeAnOldInterpolationTarget() {
        handler.handle(CustomEntityMotionPayload.enableWitherSkulls());
        skull.moveOrInterpolateTo(new Vec3(20, 64, 0), 0, 0);
        skull.snapTo(2, 65, 0, 30, 20);
        advance();
        assertEquals(new Vec3(2, 65, 0), skull.position());
        assertFalse(skull.isInterpolating());
        skull.moveOrInterpolateTo(new Vec3(30, 65, 0), 0, 0);
        handler.resetConnectionState();
        assertNull(skull.getInterpolation());
        skull.snapTo(3, 65, 0, 0, 0);
        handler.handle(CustomEntityMotionPayload.enableWitherSkulls());
        advance();
        assertEquals(new Vec3(3, 65, 0), skull.position());
    }

    private void advance() {
        skull.setOldPosAndRot();
        skull.tickCount++;
        skull.tick();
    }

    private static final class ProbeSkull extends WitherSkull {
        private ProbeSkull(Level level) {
            super(EntityType.WITHER_SKULL, level);
        }

        @Override
        public void baseTick() {
        }
    }
}
