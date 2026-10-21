package org.oryxel.viabedrockutility.payload;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Input;
import org.junit.jupiter.api.Test;
import org.oryxel.viabedrockutility.payload.enums.PayloadType;
import org.oryxel.viabedrockutility.payload.impl.player.InputLocksV3Payload;

import static org.junit.jupiter.api.Assertions.*;

class InputLocksV3PayloadTest {
    private static final Input HELD = new Input(true, false, false, true, true, true, true);

    @Test
    void carrierCanWalkAndSneakWhileManualAndAutomaticJumpRemainLocked() {
        PayloadHandler handler = new PayloadHandler();
        decode(64, 0).handle(handler);
        Input input = handler.restrictInput(HELD, false);
        assertTrue(input.forward());
        assertTrue(input.right());
        assertTrue(input.shift());
        assertFalse(input.jump());
        assertTrue(handler.isJumpInputLocked());
        assertFalse(handler.isMovementInputLocked());
        assertFalse(handler.isImmobile());
        decode(0, 0).handle(handler);
        assertEquals(HELD, handler.restrictInput(HELD, false));
        assertFalse(handler.isJumpInputLocked());
    }

    @Test
    void boatCountdownStopsAllInputThenAllowsPaddlingWithoutDismounting() {
        PayloadHandler handler = new PayloadHandler();
        decode(4 | 64 | 256, 0).handle(handler);
        assertEquals(Input.EMPTY, handler.restrictInput(HELD, true));
        assertFalse(handler.isImmobile());
        decode(64 | 256, 0).handle(handler);
        Input input = handler.restrictInput(HELD, true);
        assertTrue(input.forward());
        assertTrue(input.right());
        assertFalse(input.shift());
        assertFalse(input.jump());
        assertTrue(handler.restrictInput(HELD, false).shift());
        handler.clearInputLocks();
        assertEquals(HELD, handler.restrictInput(HELD, true));
        assertFalse(handler.isJumpInputLocked());
    }

    @Test
    void noAiAndFineGrainedPermissionsReleaseIndependently() {
        PayloadHandler handler = new PayloadHandler();
        decode(64, 1).handle(handler);
        decode(0, 1).handle(handler);
        assertEquals(Input.EMPTY, handler.restrictInput(HELD, false));
        decode(64, 0).handle(handler);
        assertTrue(handler.restrictInput(HELD, false).forward());
        assertFalse(handler.restrictInput(HELD, false).jump());
        handler.resetConnectionState();
        assertEquals(HELD, handler.restrictInput(HELD, true));
    }

    @Test
    void exactNewBodyRejectsUnknownBitsInvalidBooleansAndTrailingBytes() {
        assertEquals(15, PayloadType.INPUT_LOCKS_V3.ordinal());
        for (int flags : new int[]{-1, 1, 8, 16, 32, 128, 512}) {
            assertThrows(DecoderException.class, () -> decode(flags, 0));
        }
        assertThrows(DecoderException.class, () -> decode(0, 2));
        for (int length : new int[]{0, 1, 4, 6, 9}) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeInt(15).writeZero(length);
                assertThrows(DecoderException.class, () -> BasePayload.STREAM_CODEC.decode(buffer));
            } finally {
                buffer.release();
            }
        }
    }

    private static BasePayload decode(int flags, int immobile) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeInt(15).writeInt(flags).writeByte(immobile);
            return BasePayload.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
