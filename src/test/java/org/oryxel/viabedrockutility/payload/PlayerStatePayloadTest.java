package org.oryxel.viabedrockutility.payload;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerStatePayloadTest {

    @Test
    void encodesVersionThenMinecraftVarIntFlags() {
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            final PlayerStatePayload payload = new PlayerStatePayload((1 << 8) | 1);

            PlayerStatePayload.STREAM_CODEC.encode(buffer, payload);

            assertArrayEquals(new byte[]{1, (byte) 0x81, 0x02}, ByteBufUtil.getBytes(buffer));
            assertEquals(payload, PlayerStatePayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsUnsupportedVersionsTrailingBytesAndNegativeFlags() {
        assertDecodeFails(new byte[]{2, 0});
        assertDecodeFails(new byte[]{1, 0, 0});
        assertThrows(IllegalArgumentException.class, () -> new PlayerStatePayload(-1));
    }

    private static void assertDecodeFails(final byte[] payload) {
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload));
        try {
            assertThrows(IllegalArgumentException.class, () -> PlayerStatePayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
