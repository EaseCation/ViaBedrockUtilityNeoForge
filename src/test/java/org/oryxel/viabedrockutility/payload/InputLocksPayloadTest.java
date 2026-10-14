package org.oryxel.viabedrockutility.payload;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.client.player.ClientInput;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import org.junit.jupiter.api.Test;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.mixin.impl.accessor.ClientInputAccessor;
import org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class InputLocksPayloadTest {
    @Test
    void movementLockClearsKeysAndAxesWhileCameraRemainsAvailable() {
        final PayloadHandler handler = new PayloadHandler();
        // INPUT_LOCKS is wire type 11; movement and camera are independent Bedrock bits 4 and 2.
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

    private static ClientInput movingInput() {
        final ClientInput input = new ClientInput();
        input.keyPresses = new Input(true, false, false, false, true, false, true);
        ((ClientInputAccessor) input).vbu$setMoveVector(new Vec2(0F, 1F));
        return input;
    }

    private static BasePayload decode(final int flags, final boolean trailing) {
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeInt(11);
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
