package org.oryxel.viabedrockutility.payload.impl.skin;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamDecoder;
import org.oryxel.viabedrockutility.payload.BasePayload;

import java.util.UUID;

@RequiredArgsConstructor
@Getter
public final class BaseSkinPayload extends BasePayload {
    public static final StreamDecoder<FriendlyByteBuf, BaseSkinPayload> STREAM_DECODER = buf -> {
        final UUID playerUuid = buf.readUUID();

        int skinWidth = buf.readInt(), skinHeight = buf.readInt();

        String resourcePatch = BasePayload.readString(buf);

        String geometry = "";
        if (buf.readBoolean()) {
            geometry = BasePayload.readString(buf);
        }

        return new BaseSkinPayload(playerUuid, skinWidth, skinHeight, geometry, resourcePatch, buf.readInt());
    };

    private final UUID playerUuid;
    private final int skinWidth;
    private final int skinHeight;
    private final String geometry;
    private final String resourcePatch;
    private final int chunkCount;
}