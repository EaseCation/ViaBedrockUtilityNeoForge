package org.oryxel.viabedrockutility.payload;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.oryxel.viabedrockutility.payload.impl.player.InputLocksPayload;
import org.oryxel.viabedrockutility.payload.enums.PayloadType;

import static org.junit.jupiter.api.Assertions.*;

/** 真实解码器与锁状态生命周期共同验证，禁止把旧权限位解释为物理定身。 */
class InputLocksV2PayloadTest {
    @Test
    void v2AppendsWithoutChangingExistingOrdinalsAndAcceptsOnlyItsExactBody() {
        assertEquals(12, PayloadType.INPUT_LOCKS.ordinal());
        assertEquals(13, PayloadType.CUSTOM_ENTITY_MOTION.ordinal());
        assertEquals(14, PayloadType.INPUT_LOCKS_V2.ordinal());
        for (int size = 0; size < 9; size++) {
            if (size == 5) continue;
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeInt(14);
                buffer.writeZero(size);
                assertThrows(DecoderException.class, () -> BasePayload.STREAM_CODEC.decode(buffer));
            } finally {
                buffer.release();
            }
        }
        for (int flags : new int[]{-1, 1, 8, 16}) {
            assertThrows(DecoderException.class, () -> decode(flags, 0));
        }
        for (int value : new int[]{2, 128, 255}) {
            assertThrows(DecoderException.class, () -> decode(0, value));
        }
    }

    @Test
    void bothSourcesCanBeReleasedInEitherOrderWithoutLosingTheRemainingLock() {
        PayloadHandler handler = new PayloadHandler();
        decode(0, 1).handle(handler);
        assertTrue(handler.isImmobile());
        assertFalse(handler.isMovementInputLocked());
        assertFalse(handler.isCameraInputLocked());
        decode(6, 1).handle(handler);
        decode(6, 0).handle(handler);
        assertFalse(handler.isImmobile());
        assertTrue(handler.isMovementInputLocked());
        assertTrue(handler.isCameraInputLocked());
        decode(0, 1).handle(handler);
        assertTrue(handler.isImmobile());
        assertFalse(handler.isMovementInputLocked());
        decode(0, 0).handle(handler);
        assertFalse(handler.isMovementInputLocked());
        assertFalse(handler.isImmobile());
    }

    @Test
    void inputPermissionsAloneDoNotEnablePhysicalFreezeAndRetiredWorldsClearIt() {
        PayloadHandler handler = new PayloadHandler();
        decode(4, 0).handle(handler);
        assertTrue(handler.isMovementInputLocked());
        assertFalse(handler.isImmobile());
        decode(2, 1).handle(handler);
        handler.clearInputLocks();
        assertFalse(handler.isImmobile());
        assertFalse(handler.isMovementInputLocked());
        assertFalse(handler.isCameraInputLocked());
        decode(6, 1).handle(handler);
        handler.resetConnectionState();
        assertFalse(handler.isImmobile());
        assertFalse(handler.isMovementInputLocked());
        decode(0, 1).handle(handler);
        new InputLocksPayload(8).handle(handler);
        assertFalse(handler.isImmobile());
        assertFalse(handler.isMovementInputLocked());
    }

    private static BasePayload decode(int flags, int immobile) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeInt(14);
            buffer.writeInt(flags);
            buffer.writeByte(immobile);
            return BasePayload.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
