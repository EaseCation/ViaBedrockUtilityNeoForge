package org.oryxel.viabedrockutility.payload.impl.skin;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamDecoder;
import org.oryxel.viabedrockutility.payload.BasePayload;

import java.util.UUID;

@RequiredArgsConstructor
@Getter
public final class SkinDataPayload extends BasePayload {
    public static final StreamDecoder<FriendlyByteBuf, SkinDataPayload> STREAM_DECODER = buf -> {
        UUID playerUuid = buf.readUUID();
        int chunkPosition = buf.readInt();
        int available = buf.readableBytes();
        byte[] skinData = new byte[available];
        buf.readBytes(skinData);
        return new SkinDataPayload(playerUuid, chunkPosition, available, skinData);
    };

    private final UUID playerUuid;
    private final int chunkPosition;
    private final int available;
    private final byte[] skinData;
}