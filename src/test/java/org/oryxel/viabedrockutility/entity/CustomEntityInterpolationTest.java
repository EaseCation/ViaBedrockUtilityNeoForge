package org.oryxel.viabedrockutility.entity;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomEntityInterpolationTest {
    private static final ObjenesisStd OBJENESIS = new ObjenesisStd();
    private final ViaBedrockUtility utility = ViaBedrockUtility.getInstance();
    private final CustomEntityPayloadHandler handler = new CustomEntityPayloadHandler();
    private CustomEntityPayloadHandler previousHandler;
    private boolean previousViaBedrockPresent;

    @BeforeEach
    void installHandler() throws ReflectiveOperationException {
        previousHandler = utility.getPayloadHandler();
        previousViaBedrockPresent = utility.isViaBedrockPresent();
        setHandler(handler);
        utility.setViaBedrockPresent(true);
    }

    @AfterEach
    void restoreHandler() throws ReflectiveOperationException {
        setHandler(previousHandler);
        utility.setViaBedrockPresent(previousViaBedrockPresent);
    }

    @Test
    void customModelMovesOverThreeClientTicks() {
        final Interaction entity = customEntity();
        final InterpolationHandler interpolation = entity.getInterpolation();
        assertNotNull(interpolation);

        entity.moveOrInterpolateTo(new Vec3(3, 6, 9), 90, 30);
        assertEquals(Vec3.ZERO, entity.position());
        assertTrue(interpolation.hasActiveInterpolation());
        entity.tick();
        assertEquals(new Vec3(1, 2, 3), entity.position());
        assertEquals(30, entity.getYRot(), 0.0001);
        entity.tick();
        assertEquals(new Vec3(2, 4, 6), entity.position());
        entity.tick();
        assertEquals(new Vec3(3, 6, 9), entity.position());
        assertEquals(90, entity.getYRot(), 0.0001);
        assertFalse(interpolation.hasActiveInterpolation());
    }

    @Test
    void newestMovementTargetReplacesAnUnfinishedMove() {
        final Interaction entity = customEntity();
        entity.moveOrInterpolateTo(new Vec3(3, 0, 0), 0, 0);
        entity.tick();
        entity.moveOrInterpolateTo(new Vec3(4, 0, 0), 0, 0);
        entity.tick();
        assertEquals(2, entity.getX(), 0.0001);
        entity.tick();
        entity.tick();
        assertEquals(4, entity.getX(), 0.0001);
    }

    @Test
    void explicitPositionUpdateCancelsAnOlderMovementTarget() {
        final Interaction entity = customEntity();
        entity.moveOrInterpolateTo(new Vec3(3, 0, 0), 0, 0);
        entity.tick();
        entity.setPos(100, 10, 20);
        assertFalse(entity.getInterpolation().hasActiveInterpolation());
        entity.tick();
        assertEquals(new Vec3(100, 10, 20), entity.position());
    }

    @Test
    void snapCancelsMovementAndResetsPreviousRenderPosition() {
        final Interaction entity = customEntity();
        entity.moveOrInterpolateTo(new Vec3(3, 0, 0), 0, 0);
        entity.tick();
        entity.snapTo(100, 10, 20, 90, 30);
        assertFalse(entity.getInterpolation().hasActiveInterpolation());
        entity.tick();
        assertEquals(new Vec3(100, 10, 20), entity.position());
        assertEquals(100, entity.xOld, 0.0001);
        assertEquals(90, entity.getYRot(), 0.0001);
    }

    @Test
    void removingModelRecognitionStopsPendingInterpolation() {
        final Interaction entity = customEntity();
        final InterpolationHandler interpolation = entity.getInterpolation();
        entity.moveOrInterpolateTo(new Vec3(3, 0, 0), 0, 0);
        entity.tick();
        handler.getCachedCustomEntities().remove(entity.getUUID());
        assertNull(entity.getInterpolation());
        entity.tick();
        assertFalse(interpolation.hasActiveInterpolation());
        assertEquals(1, entity.getX(), 0.0001);
    }

    @Test
    void ordinaryInteractionAndDisconnectedModelsKeepImmediateMovement() {
        final Interaction ordinary = new Interaction(EntityType.INTERACTION, null);
        assertNull(ordinary.getInterpolation());
        ordinary.moveOrInterpolateTo(new Vec3(3, 0, 0), 90, 0);
        ordinary.tick();
        assertEquals(3, ordinary.getX(), 0.0001);

        final Interaction custom = customEntity();
        utility.setViaBedrockPresent(false);
        assertNull(custom.getInterpolation());
        custom.moveOrInterpolateTo(new Vec3(3, 0, 0), 90, 0);
        custom.tick();
        assertEquals(3, custom.getX(), 0.0001);
    }

    private Interaction customEntity() {
        final Interaction entity = new Interaction(EntityType.INTERACTION,
                OBJENESIS.newInstance(CollisionFreeLevel.class));
        handler.getCachedCustomEntities().put(entity.getUUID(),
                OBJENESIS.newInstance(CustomEntityTicker.class));
        return entity;
    }

    private static final class CollisionFreeLevel extends ClientLevel {
        private CollisionFreeLevel() {
            super(null, null, null, null, 0, 0, null, true, 0L, 0);
        }

        @Override
        public boolean noCollision(Entity entity, AABB bounds) {
            return true;
        }
    }

    private void setHandler(CustomEntityPayloadHandler payloadHandler) throws ReflectiveOperationException {
        final Field field = ViaBedrockUtility.class.getDeclaredField("payloadHandler");
        field.setAccessible(true);
        field.set(utility, payloadHandler);
    }
}
