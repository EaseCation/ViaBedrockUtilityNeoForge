package org.oryxel.viabedrockutility.payload;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.oryxel.viabedrockutility.payload.impl.entity.AnimatePayload;
import org.oryxel.viabedrockutility.payload.impl.player.PlayerAnimatePayload;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerAnimatePayloadTest {
    private static final UUID PLAYER_UUID = UUID.fromString("01234567-89ab-cdef-fedc-ba9876543210");
    private static final String ANIMATION = "animation.圣符.开场_🎬";
    private static final String NEXT_STATE = "剧情结束";
    private static final String STOP_EXPRESSION = "variable.剧情结束 == 1;";
    private static final String CONTROLLER = "controller.玩家.剧情";
    private static final int STOP_EXPRESSION_VERSION = 0x01020304;

    @Test
    void decodesEveryFieldFromBigEndianWireWithUtf8ByteLengths() throws IOException {
        final PlayerAnimatePayload payload = assertInstanceOf(PlayerAnimatePayload.class,
                decode(playerAnimationWire(NEXT_STATE, STOP_EXPRESSION, CONTROLLER, 0.375F)));

        assertEquals(PLAYER_UUID, payload.getPlayerUuid());
        assertEquals(ANIMATION, payload.getAnimation());
        assertEquals(NEXT_STATE, payload.getNextState());
        assertEquals(STOP_EXPRESSION, payload.getStopExpression());
        assertEquals(STOP_EXPRESSION_VERSION, payload.getStopExpressionVersion());
        assertEquals(CONTROLLER, payload.getController());
        assertEquals(0.375F, payload.getBlendOutTime());
    }

    @Test
    void acceptsEmptyOptionalStringsAndZeroBlendOut() throws IOException {
        final PlayerAnimatePayload payload = assertInstanceOf(PlayerAnimatePayload.class,
                decode(playerAnimationWire("", "", "", 0.0F)));

        assertEquals(ANIMATION, payload.getAnimation());
        assertEquals("", payload.getNextState());
        assertEquals("", payload.getStopExpression());
        assertEquals("", payload.getController());
        assertEquals(STOP_EXPRESSION_VERSION, payload.getStopExpressionVersion());
        assertEquals(0.0F, payload.getBlendOutTime());
    }

    @Test
    void legacyOrdinalTwoStillDecodesWithoutNewControllerFields() throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (final DataOutputStream wire = new DataOutputStream(bytes)) {
            wire.writeInt(2);
            writeUuid(wire);
            writeString(wire, ANIMATION);
        }

        final AnimatePayload payload = assertInstanceOf(AnimatePayload.class, decode(bytes.toByteArray()));
        assertEquals(PLAYER_UUID, payload.getEntityUuid());
        assertEquals(ANIMATION, payload.getAnimationName());
    }

    @Test
    void rejectsInvalidLengthForEveryStringField() throws IOException {
        final byte[] valid = playerAnimationWire(NEXT_STATE, STOP_EXPRESSION, CONTROLLER, 0.25F);
        final int animationOffset = Integer.BYTES + 2 * Long.BYTES;
        final int nextStateOffset = animationOffset + stringWireSize(ANIMATION);
        final int stopExpressionOffset = nextStateOffset + stringWireSize(NEXT_STATE);
        final int controllerOffset = stopExpressionOffset + stringWireSize(STOP_EXPRESSION) + Integer.BYTES;

        for (final int offset : new int[]{animationOffset, nextStateOffset, stopExpressionOffset, controllerOffset}) {
            for (final int invalidLength : new int[]{-1, 1_048_577, valid.length}) {
                final byte[] invalid = valid.clone();
                ByteBuffer.wrap(invalid).putInt(offset, invalidLength);
                assertThrows(DecoderException.class, () -> decode(invalid),
                        "string offset " + offset + ", byte length " + invalidLength);
            }
        }
    }

    @Test
    void rejectsEveryTruncatedPrefixIncludingPartialUuidVersionAndFloat() throws IOException {
        final byte[] valid = playerAnimationWire(NEXT_STATE, STOP_EXPRESSION, CONTROLLER, 0.25F);
        for (int length = 0; length < valid.length; length++) {
            final byte[] truncated = Arrays.copyOf(valid, length);
            final RuntimeException failure = assertThrows(RuntimeException.class, () -> decode(truncated),
                    "truncated at byte " + length);
            assertTrue(failure instanceof DecoderException || failure instanceof IndexOutOfBoundsException,
                    "expected a wire decoding failure at byte " + length + ", got " + failure);
        }
    }

    @Test
    void rejectsNonFiniteOrNegativeBlendOut() throws IOException {
        for (final float invalidBlend : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.25F}) {
            final byte[] invalid = playerAnimationWire(NEXT_STATE, STOP_EXPRESSION, CONTROLLER, invalidBlend);
            assertThrows(DecoderException.class, () -> decode(invalid), "blend-out " + invalidBlend);
        }
    }

    @Test
    void rejectsTrailingBytesAfterCompletePlayerAnimation() throws IOException {
        final byte[] valid = playerAnimationWire(NEXT_STATE, STOP_EXPRESSION, CONTROLLER, 0.25F);
        final byte[] withTrailingByte = Arrays.copyOf(valid, valid.length + 1);
        assertThrows(DecoderException.class, () -> decode(withTrailingByte));
    }

    private static byte[] playerAnimationWire(final String nextState, final String stopExpression,
                                              final String controller, final float blendOutTime) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        // Use a JDK wire writer rather than the decoder's Minecraft/Netty helpers.
        try (final DataOutputStream wire = new DataOutputStream(bytes)) {
            wire.writeInt(11);
            writeUuid(wire);
            writeString(wire, ANIMATION);
            writeString(wire, nextState);
            writeString(wire, stopExpression);
            wire.writeInt(STOP_EXPRESSION_VERSION);
            writeString(wire, controller);
            wire.writeFloat(blendOutTime);
        }
        return bytes.toByteArray();
    }

    private static void writeUuid(final DataOutputStream wire) throws IOException {
        wire.writeLong(PLAYER_UUID.getMostSignificantBits());
        wire.writeLong(PLAYER_UUID.getLeastSignificantBits());
    }

    private static void writeString(final DataOutputStream wire, final String value) throws IOException {
        final byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        wire.writeInt(utf8.length);
        wire.write(utf8);
    }

    private static int stringWireSize(final String value) {
        return Integer.BYTES + value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static BasePayload decode(final byte[] wire) {
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(wire));
        try {
            return BasePayload.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
