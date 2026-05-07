package org.oryxel.viabedrockutility.payload.impl.skin;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.resources.ResourceLocation;
import org.oryxel.viabedrockutility.fabric.ViaBedrockUtilityFabric;
import org.oryxel.viabedrockutility.payload.BasePayload;

import java.util.UUID;

@RequiredArgsConstructor
@Getter
public final class CapeDataPayload extends BasePayload {
    public static final StreamDecoder<FriendlyByteBuf, CapeDataPayload> STREAM_DECODER = buf -> {
        UUID playerUuid = buf.readUUID();
        int width = buf.readInt();
        int height = buf.readInt();

        String capeId = BasePayload.readString(buf);
        ResourceLocation identifier = ResourceLocation.fromNamespaceAndPath(ViaBedrockUtilityFabric.MOD_ID, capeId);

        byte[] capeData = new byte[buf.readInt()];
        buf.readBytes(capeData);
        return new CapeDataPayload(playerUuid, width, height, identifier, capeData);
    };

    private final UUID playerUuid;
    private final int width;
    private final int height;
    private final ResourceLocation identifier;
    private final byte[] capeData;
}
