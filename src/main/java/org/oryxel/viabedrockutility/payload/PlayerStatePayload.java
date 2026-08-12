package org.oryxel.viabedrockutility.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;

public record PlayerStatePayload(int stateFlags) implements CustomPacketPayload {

    public static final int PROTOCOL_VERSION = 1;
    public static final Type<PlayerStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            ViaBedrockUtilityNeoForge.MOD_ID, "player_state"
    ));
    public static final StreamCodec<FriendlyByteBuf, PlayerStatePayload> STREAM_CODEC = StreamCodec.of(
            PlayerStatePayload::encode,
            PlayerStatePayload::decode
    );

    public PlayerStatePayload {
        if (stateFlags < 0) {
            throw new IllegalArgumentException("Player state flags must not be negative");
        }
    }

    private static void encode(final FriendlyByteBuf buffer, final PlayerStatePayload payload) {
        buffer.writeByte(PROTOCOL_VERSION);
        buffer.writeVarInt(payload.stateFlags);
    }

    private static PlayerStatePayload decode(final FriendlyByteBuf buffer) {
        final int protocolVersion = buffer.readUnsignedByte();
        if (protocolVersion != PROTOCOL_VERSION) {
            throw new IllegalArgumentException("Unsupported player state protocol version: " + protocolVersion);
        }

        final PlayerStatePayload payload = new PlayerStatePayload(buffer.readVarInt());
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("Trailing bytes in player state payload");
        }
        return payload;
    }

    @Override
    public Type<PlayerStatePayload> type() {
        return TYPE;
    }
}
