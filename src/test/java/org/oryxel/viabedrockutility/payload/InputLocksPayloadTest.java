package org.oryxel.viabedrockutility.payload;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.MouseHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.SmoothDouble;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import org.junit.jupiter.api.Test;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.mixin.impl.accessor.ClientInputAccessor;
import org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler;
import org.oryxel.viabedrockutility.payload.enums.PayloadType;
import org.oryxel.viabedrockutility.payload.impl.player.InputLocksPayload;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InputLocksPayloadTest {
    @Test
    void inputLocksAppendAfterTheExistingTwelveWireTypes() {
        assertEquals(List.of("CONFIRM", "MODEL_REQUEST", "ANIMATE", "CAPE", "SKIN_INFORMATION", "SKIN_DATA",
                "SKIN_ANIMATION_INFO", "SKIN_ANIMATION_DATA", "SPAWN_PARTICLE", "SPAWN_PARTICLE_V2",
                "PLAYER_VISUAL_STATE", "PLAYER_ANIMATE", "INPUT_LOCKS", "CUSTOM_ENTITY_MOTION"),
                Arrays.stream(PayloadType.values()).map(Enum::name).toList());
        assertEquals(11, PayloadType.PLAYER_ANIMATE.ordinal());
        assertEquals(12, PayloadType.INPUT_LOCKS.ordinal());
        assertEquals(6, ((InputLocksPayload) decode(6, false)).getFlags());
    }

    @Test
    void loginAdvertisementAddsInputLockCapabilityWithoutDroppingExistingChannels() {
        assertEquals(Set.of("viabedrockutility:confirm", "viabedrockutility:particle_runtime_v2",
                        "viabedrockutility:player_visual_state_v1", "viabedrockutility:player_animation_v1",
                        "viabedrockutility:input_locks_v1", "viabedrockutility:custom_entity_motion_v1",
                        "becamera:confirm", "viabedrockutility:player_state"),
                ViaBedrockUtility.registrationChannels().stream().map(ResourceLocation::toString)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void unsupportedBedrockBitsFromOlderProxiesDoNotLockMovementOrDisconnect() {
        final PayloadHandler handler = new PayloadHandler();
        decode(InputLocksPayload.CAMERA | 16, false).handle(handler);
        assertTrue(handler.isCameraInputLocked());
        assertFalse(handler.isMovementInputLocked());
        decode(16, false).handle(handler);
        assertFalse(handler.isCameraInputLocked());
        assertFalse(handler.isMovementInputLocked());
        assertEquals(6, ((InputLocksPayload) decode(-1, false)).getFlags());
    }

    @Test
    void updatesReplaceBothFlagsBeforeResourcePacksLoad() {
        final PayloadHandler handler = new PayloadHandler();
        assertNull(handler.getPackManager());
        decode(6, false).handle(handler);
        assertTrue(handler.isCameraInputLocked());
        assertTrue(handler.isMovementInputLocked());
        decode(2, false).handle(handler);
        assertTrue(handler.isCameraInputLocked());
        assertFalse(handler.isMovementInputLocked());
        decode(4, false).handle(handler);
        assertFalse(handler.isCameraInputLocked());
        assertTrue(handler.isMovementInputLocked());
        decode(0, false).handle(handler);
        assertFalse(handler.isCameraInputLocked());
        assertFalse(handler.isMovementInputLocked());
    }

    @Test
    void movementLockClearsKeysAndAxesWhileCameraRemainsAvailable() {
        final PayloadHandler handler = new PayloadHandler();
        // 输入锁使用协议编号 12；移动和相机分别由基岩版标志 4、2 控制。
        decode(4, false).handle(handler);
        final ClientInput input = movingInput();
        handler.onMovementInputUpdate(new MovementInputUpdateEvent(null, input));

        assertEquals(Input.EMPTY, input.keyPresses);
        assertEquals(Vec2.ZERO, input.getMoveVector());
        assertFalse(handler.isCameraInputLocked());

        decode(0, false).handle(handler);
        final ClientInput unlocked = movingInput();
        handler.onMovementInputUpdate(new MovementInputUpdateEvent(null, unlocked));
        assertTrue(unlocked.keyPresses.forward());
        assertTrue(unlocked.keyPresses.jump());
        assertEquals(1F, unlocked.getMoveVector().y);
    }

    @Test
    void cameraLockPreservesMovementAndConnectionResetClearsBothLocks() {
        final PayloadHandler handler = new PayloadHandler();
        decode(2, false).handle(handler);
        final ClientInput input = movingInput();
        handler.onMovementInputUpdate(new MovementInputUpdateEvent(null, input));
        assertTrue(handler.isCameraInputLocked());
        assertTrue(input.keyPresses.forward());
        assertTrue(input.keyPresses.jump());
        assertEquals(1F, input.getMoveVector().y);

        decode(6, false).handle(handler);
        handler.resetConnectionState();
        final ClientInput afterDisconnect = movingInput();
        handler.onMovementInputUpdate(new MovementInputUpdateEvent(null, afterDisconnect));
        assertFalse(handler.isCameraInputLocked());
        assertTrue(afterDisconnect.keyPresses.forward());
        assertTrue(afterDisconnect.keyPresses.jump());

        decode(6, false).handle(handler);
        handler.clearInputLocks();
        final ClientInput afterLevelChange = movingInput();
        handler.onMovementInputUpdate(new MovementInputUpdateEvent(null, afterLevelChange));
        assertFalse(handler.isCameraInputLocked());
        assertTrue(afterLevelChange.keyPresses.forward());
    }

    @Test
    void lockedAutoJumpCannotReenableJumpAfterTheMovementEvent() throws Exception {
        final ViaBedrockUtility utility = ViaBedrockUtility.getInstance();
        final Field handlerField = ViaBedrockUtility.class.getDeclaredField("payloadHandler");
        handlerField.setAccessible(true);
        final Object previousHandler = handlerField.get(utility);
        final CustomEntityPayloadHandler handler = new CustomEntityPayloadHandler();
        try {
            handlerField.set(utility, handler);
            decode(4, false).handle(handler);
            final ClientInput input = movingInput();
            handler.onMovementInputUpdate(new MovementInputUpdateEvent(null, input));
            input.makeJump();
            assertFalse(input.keyPresses.jump());

            decode(0, false).handle(handler);
            input.makeJump();
            assertTrue(input.keyPresses.jump());
        } finally {
            handlerField.set(utility, previousHandler);
        }
    }

    @Test
    void rejectsExtraBytesInAnInputLockMessage() {
        assertThrows(DecoderException.class, () -> decode(4, true));
    }

    @Test
    void rejectsMissingFlagBytesAndUnknownOrdinals() {
        for (int size = 0; size < Integer.BYTES; size++) {
            final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeInt(12);
                buffer.writeZero(size);
                assertThrows(DecoderException.class, () -> BasePayload.STREAM_CODEC.decode(buffer));
            } finally {
                buffer.release();
            }
        }
        final FriendlyByteBuf unknown = new FriendlyByteBuf(Unpooled.buffer());
        try {
            unknown.writeInt(13);
            unknown.writeInt(0);
            assertThrows(DecoderException.class, () -> BasePayload.STREAM_CODEC.decode(unknown));
        } finally {
            unknown.release();
        }
    }

    @Test
    void cameraLockSkipsPlayerTurningAndClearsCinematicRemainder() throws Exception {
        final ViaBedrockUtility utility = ViaBedrockUtility.getInstance();
        final Field handlerField = ViaBedrockUtility.class.getDeclaredField("payloadHandler");
        handlerField.setAccessible(true);
        final Object previousHandler = handlerField.get(utility);
        final CustomEntityPayloadHandler handler = new CustomEntityPayloadHandler();
        final MouseHandler mouse = new MouseHandler(null);
        final Method turnPlayer = MouseHandler.class.getDeclaredMethod("turnPlayer", double.class);
        turnPlayer.setAccessible(true);
        final Field smoothingField = MouseHandler.class.getDeclaredField("smoothTurnX");
        smoothingField.setAccessible(true);
        final SmoothDouble smoothing = (SmoothDouble) smoothingField.get(mouse);
        smoothing.getNewDeltaValue(10, .5);
        try {
            handlerField.set(utility, handler);
            decode(2, false).handle(handler);
            // A locked turn must return before vanilla dereferences the absent Minecraft instance.
            turnPlayer.invoke(mouse, .05);
            assertEquals(0.0, smoothing.getNewDeltaValue(0, 1));

            decode(0, false).handle(handler);
            InvocationTargetException unlocked = assertThrows(InvocationTargetException.class,
                    () -> turnPlayer.invoke(mouse, .05));
            assertInstanceOf(NullPointerException.class, unlocked.getCause());
        } finally {
            handlerField.set(utility, previousHandler);
        }
    }

    private static ClientInput movingInput() {
        final ClientInput input = new ClientInput();
        input.keyPresses = new Input(true, false, false, false, true, false, true);
        ((ClientInputAccessor) input).vbu$setMoveVector(new Vec2(0F, 1F));
        return input;
    }

    private static BasePayload decode(final int flags, final boolean trailing) {
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeInt(12);
            buffer.writeInt(flags);
            if (trailing) {
                buffer.writeByte(1);
            }
            return BasePayload.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
