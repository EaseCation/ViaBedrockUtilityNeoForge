package org.oryxel.viabedrockutility.payload;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.oryxel.viabedrockutility.payload.enums.PayloadType;
import org.oryxel.viabedrockutility.payload.impl.entity.CustomEntityMotionPayload;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CustomEntityMotionPayloadTest {
    private static final UUID UUID_VALUE = UUID.fromString("01234567-89ab-cdef-fedc-ba9876543210");

    @Test
    void decodesBigEndianTeleportWithoutChangingTheExistingOrdinals() {
        final CustomEntityMotionPayload packet = assertInstanceOf(CustomEntityMotionPayload.class, decode(wire()));
        assertEquals(13, PayloadType.CUSTOM_ENTITY_MOTION.ordinal());
        assertEquals(12, PayloadType.INPUT_LOCKS.ordinal());
        assertEquals(UUID_VALUE, packet.getUuid());
        assertEquals(1.25, packet.getX());
        assertEquals(64.5, packet.getY());
        assertEquals(-3.75, packet.getZ());
        assertEquals(179.5F, packet.getYaw());
        assertEquals(-20.0F, packet.getPitch());
        assertTrue(packet.isOnGround());
    }

    @Test
    void confirmationWorksBeforePackLoadingAndDoesNotSurviveAConnectionReset() {
        final PayloadHandler handler = new PayloadHandler();
        assertFalse(handler.isCustomEntityMotionEnabled());
        decode(new byte[]{0, 0, 0, 13, 0}).handle(handler);
        assertTrue(handler.isCustomEntityMotionEnabled());
        assertNull(handler.getPackManager());
        handler.resetConnectionState();
        assertFalse(handler.isCustomEntityMotionEnabled());
    }

    @Test
    void rejectsEveryTruncationUnknownActionAndTrailingBytes() {
        final byte[] wire = wire();
        for (int length = 4; length < wire.length; length++) {
            final byte[] truncated = Arrays.copyOf(wire, length);
            assertThrows(DecoderException.class, () -> decode(truncated));
        }
        assertThrows(DecoderException.class, () -> decode(Arrays.copyOf(wire, wire.length + 1)));
        assertThrows(DecoderException.class, () -> decode(new byte[]{0, 0, 0, 13, 4}));
        assertThrows(DecoderException.class, () -> decode(new byte[]{0, 0, 0, 13, 0, 1}));
        assertThrows(DecoderException.class, () -> decode(new byte[]{0, 0, 0, 13, 2, 1}));
    }

    @Test
    void skullMotionNegotiatesIndependentlyAndResetsWithTheConnection() {
        final PayloadHandler handler = new PayloadHandler();
        decode(new byte[]{0, 0, 0, 13, 0}).handle(handler);
        assertFalse(handler.isWitherSkullMotionEnabled());
        decode(new byte[]{0, 0, 0, 13, 2}).handle(handler);
        assertTrue(handler.isWitherSkullMotionEnabled());
        assertNull(handler.getPackManager());
        handler.resetConnectionState();
        assertFalse(handler.isWitherSkullMotionEnabled());
    }

    @Test
    void skullTeleportsKeepTheValidatedBigEndianSnapshotFormat() {
        final byte[] wire = wire();
        wire[4] = 3;
        final CustomEntityMotionPayload packet = assertInstanceOf(CustomEntityMotionPayload.class, decode(wire));
        assertEquals(CustomEntityMotionPayload.TELEPORT_WITHER_SKULL, packet.getAction());
        assertEquals(UUID_VALUE, packet.getUuid());
        assertEquals(64.5, packet.getY());
        for (int length = 4; length < wire.length; length++) {
            final byte[] truncated = Arrays.copyOf(wire, length);
            assertThrows(DecoderException.class, () -> decode(truncated));
        }
        assertThrows(DecoderException.class, () -> decode(Arrays.copyOf(wire, wire.length + 1)));
    }

    @Test
    void rejectsNonFiniteCoordinatesRotationAndInvalidGroundFlag() {
        for (int offset : new int[]{21, 29, 37}) {
            final byte[] invalid = wire();
            ByteBuffer.wrap(invalid).putDouble(offset, Double.NaN);
            assertThrows(DecoderException.class, () -> decode(invalid));
        }
        for (int offset : new int[]{45, 49}) {
            final byte[] invalid = wire();
            ByteBuffer.wrap(invalid).putFloat(offset, Float.POSITIVE_INFINITY);
            assertThrows(DecoderException.class, () -> decode(invalid));
        }
        final byte[] invalid = wire();
        invalid[invalid.length - 1] = 2;
        assertThrows(DecoderException.class, () -> decode(invalid));
    }

    private static byte[] wire() {
        return ByteBuffer.allocate(5 + CustomEntityMotionPayload.TELEPORT_BYTES)
                .putInt(13).put((byte) 1)
                .putLong(UUID_VALUE.getMostSignificantBits()).putLong(UUID_VALUE.getLeastSignificantBits())
                .putDouble(1.25).putDouble(64.5).putDouble(-3.75)
                .putFloat(179.5F).putFloat(-20F).put((byte) 1).array();
    }

    private static BasePayload decode(byte[] wire) {
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(wire));
        try {
            return BasePayload.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
