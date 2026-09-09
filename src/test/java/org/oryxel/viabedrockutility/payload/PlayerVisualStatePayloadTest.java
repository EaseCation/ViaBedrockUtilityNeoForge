package org.oryxel.viabedrockutility.payload;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.oryxel.viabedrockutility.payload.impl.player.PlayerVisualStatePayload;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerVisualStatePayloadTest {
    private static final int PLAYER_VISUAL_STATE_ORDINAL = 10;

    @Test
    void decodesAndAppliesCustomSpectatorState() {
        final UUID playerUuid = UUID.randomUUID();
        final BasePayload decoded = decode(playerUuid, PlayerVisualStatePayload.CUSTOM_SPECTATOR, false);
        final PlayerVisualStatePayload payload = assertInstanceOf(PlayerVisualStatePayload.class, decoded);
        final PayloadHandler handler = new PayloadHandler();

        handler.handle(payload);
        assertTrue(handler.isPlayerTranslucent(playerUuid));

        handler.handle(new PlayerVisualStatePayload(playerUuid, 0));
        assertFalse(handler.isPlayerTranslucent(playerUuid));
    }

    @Test
    void entityRemovalAndConnectionResetClearState() {
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();
        final PayloadHandler handler = new PayloadHandler();
        handler.handle(new PlayerVisualStatePayload(first, PlayerVisualStatePayload.CUSTOM_SPECTATOR));
        handler.handle(new PlayerVisualStatePayload(second, PlayerVisualStatePayload.CUSTOM_SPECTATOR));

        handler.removeCustomEntity(first);
        assertFalse(handler.isPlayerTranslucent(first));
        assertTrue(handler.isPlayerTranslucent(second));

        handler.resetConnectionState();
        assertFalse(handler.isPlayerTranslucent(second));
    }

    @Test
    void rejectsUnknownFlagsAndTrailingBytes() {
        assertThrows(DecoderException.class, () -> decode(UUID.randomUUID(), 2, false));
        assertThrows(DecoderException.class, () -> decode(UUID.randomUUID(), 0, true));
    }

    private static BasePayload decode(final UUID playerUuid, final int flags, final boolean trailing) {
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeInt(PLAYER_VISUAL_STATE_ORDINAL);
            buffer.writeUUID(playerUuid);
            buffer.writeByte(flags);
            if (trailing) {
                buffer.writeByte(1);
            }
            return BasePayload.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
