package org.oryxel.viabedrockutility.payload.impl.skin;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamDecoder;
import org.oryxel.viabedrockutility.payload.BasePayload;

import java.util.UUID;

@RequiredArgsConstructor
@Getter
public final class SkinAnimationDataPayload extends BasePayload {
    public static final StreamDecoder<FriendlyByteBuf, SkinAnimationDataPayload> STREAM_DECODER = buf -> {
        UUID playerUuid = buf.readUUID();
        int animIndex = buf.readInt();
        int chunkPosition = buf.readInt();
        int available = buf.readableBytes();
        byte[] data = new byte[available];
        buf.readBytes(data);
        return new SkinAnimationDataPayload(playerUuid, animIndex, chunkPosition, data);
    };

    private final UUID playerUuid;
    private final int animIndex;
    private final int chunkPosition;
    private final byte[] data;
}
