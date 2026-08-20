package org.oryxel.viabedrockutility.payload;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.oryxel.viabedrockutility.payload.impl.particle.SpawnParticleV2Payload;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpawnParticleV2PayloadTest {
    private static final int SPAWN_PARTICLE_V2_ORDINAL = 9;

    @Test
    void decodesAConsistentEntityAnchor() {
        final UUID owner = UUID.randomUUID();
        final BasePayload decoded = decode(buffer -> {
            buffer.writeInt(SPAWN_PARTICLE_V2_ORDINAL);
            writeString(buffer, "easecation:bullet_laser");
            buffer.writeByte(SpawnParticleV2Payload.ENTITY_ANCHOR);
            buffer.writeBoolean(true);
            buffer.writeUUID(owner);
            buffer.writeFloat(1f); buffer.writeFloat(2f); buffer.writeFloat(3f);
            buffer.writeBoolean(false);
        });

        final SpawnParticleV2Payload payload = (SpawnParticleV2Payload) decoded;
        assertEquals(owner, payload.getOwnerUuid());
        assertEquals(SpawnParticleV2Payload.ENTITY_ANCHOR, payload.getAnchorKind());
    }

    @Test
    void rejectsInvalidKindsMissingOwnersAndTrailingBytes() {
        assertDecodeFails(buffer -> writeMinimal(buffer, 2, false, false));
        assertDecodeFails(buffer -> writeMinimal(buffer, SpawnParticleV2Payload.ENTITY_ANCHOR, false, false));
        assertDecodeFails(buffer -> writeMinimal(buffer, SpawnParticleV2Payload.WORLD_ANCHOR, false, true));
    }

    @Test
    void rejectsNegativeTypesAndTruncatedStrings() {
        assertDecodeFails(buffer -> buffer.writeInt(-1));
        assertDecodeFails(buffer -> {
            buffer.writeInt(SPAWN_PARTICLE_V2_ORDINAL);
            buffer.writeInt(128);
            buffer.writeByte('x');
        });
    }

    private static void writeMinimal(FriendlyByteBuf buffer, int anchorKind, boolean owner, boolean trailing) {
        buffer.writeInt(SPAWN_PARTICLE_V2_ORDINAL);
        writeString(buffer, "test:particle");
        buffer.writeByte(anchorKind);
        buffer.writeBoolean(owner);
        if (owner) buffer.writeUUID(UUID.randomUUID());
        buffer.writeFloat(0f); buffer.writeFloat(0f); buffer.writeFloat(0f);
        buffer.writeBoolean(false);
        if (trailing) buffer.writeByte(1);
    }

    private static void writeString(FriendlyByteBuf buffer, String value) {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buffer.writeInt(bytes.length);
        buffer.writeBytes(bytes);
    }

    private static BasePayload decode(Consumer<FriendlyByteBuf> writer) {
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writer.accept(buffer);
            return BasePayload.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static void assertDecodeFails(Consumer<FriendlyByteBuf> writer) {
        assertThrows(DecoderException.class, () -> decode(writer));
    }
}
