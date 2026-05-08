package org.oryxel.viabedrockutility.payload;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.enums.bedrock.ActorFlags;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import org.oryxel.viabedrockutility.payload.enums.PayloadType;
import org.oryxel.viabedrockutility.payload.impl.entity.*;
import org.oryxel.viabedrockutility.payload.impl.particle.*;
import org.oryxel.viabedrockutility.payload.impl.skin.*;
import org.oryxel.viabedrockutility.util.EnumUtil;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
@Getter
public class BasePayload implements CustomPacketPayload {
    public static final Type<BasePayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(ViaBedrockUtilityNeoForge.MOD_ID, "data"));

    public static final StreamCodec<FriendlyByteBuf, BasePayload> STREAM_CODEC = StreamCodec.of(null, buf -> {
        final int type = buf.readInt();
        if (type > PayloadType.values().length - 1) {
            throw new RuntimeException("Invalid type: " + type);
        }

        switch (PayloadType.values()[type]) {
            case CONFIRM -> {
                // Confirm that ViaBedrock is present, this should be sent back right after we send confirm register channel.
                ViaBedrockUtility.getInstance().setViaBedrockPresent(true);
                ViaBedrockUtilityNeoForge.LOGGER.info("[Handshake] Received CONFIRM from ViaBedrock, handshake successful!");
                return new BasePayload();
            }
            case MODEL_REQUEST -> {
                final java.util.UUID uuid = buf.readUUID();
                final String identifier = readString(buf);

                BigInteger combinedFlags = BigInteger.ZERO;
                if (buf.readBoolean()) {
                    combinedFlags = combinedFlags.add(BigInteger.valueOf(buf.readLong()));
                }
                if (buf.readBoolean()) {
                    combinedFlags = combinedFlags.add(BigInteger.valueOf(buf.readLong()).shiftLeft(64));
                }

                Integer variant = null, mark_variant = null, skinId = null;
                Float scale = null;
                if (buf.readBoolean()) {
                    variant = buf.readInt();
                }
                if (buf.readBoolean()) {
                    mark_variant = buf.readInt();
                }
                if (buf.readBoolean()) {
                    skinId = buf.readInt();
                }
                if (buf.readBoolean()) {
                    scale = buf.readFloat();
                }

                return new ModelRequestPayload(identifier, EnumUtil.getEnumSetFromBitmask(ActorFlags.class, combinedFlags, ActorFlags::getValue), variant, mark_variant, skinId, scale, uuid);
            }

            case ANIMATE -> {
                // TODO: Implement this.
                return new BasePayload();
            }

            case CAPE -> {
                return CapeDataPayload.STREAM_DECODER.decode(buf);
            }
            case SKIN_INFORMATION -> {
                return BaseSkinPayload.STREAM_DECODER.decode(buf);
            }
            case SKIN_DATA -> {
                return SkinDataPayload.STREAM_DECODER.decode(buf);
            }
            case SKIN_ANIMATION_INFO -> {
                return SkinAnimationInfoPayload.STREAM_DECODER.decode(buf);
            }
            case SKIN_ANIMATION_DATA -> {
                return SkinAnimationDataPayload.STREAM_DECODER.decode(buf);
            }

            case SPAWN_PARTICLE -> {
                final String identifier = readString(buf);
                final float x = buf.readFloat();
                final float y = buf.readFloat();
                final float z = buf.readFloat();
                final String molangVarsJson = buf.readBoolean() ? readString(buf) : "";
                ViaBedrockUtilityNeoForge.LOGGER.info("[Particle:L3] Decoded SPAWN_PARTICLE payload: {} at ({}, {}, {})", identifier, x, y, z);
                return new SpawnParticlePayload(identifier, x, y, z, molangVarsJson);
            }

            default -> throw new IllegalStateException("Unexpected value: " + PayloadType.values()[type]);
        }
    });

    public void handle(final PayloadHandler handler) {
        try {
            handler.handle(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static String readString(FriendlyByteBuf buf) {
        int length = buf.readInt();
        String result = buf.toString(buf.readerIndex(), length, StandardCharsets.UTF_8);
        buf.readerIndex(buf.readerIndex() + length);
        return result;
    }
}
