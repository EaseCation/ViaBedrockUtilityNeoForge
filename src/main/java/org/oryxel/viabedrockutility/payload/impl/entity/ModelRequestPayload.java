package org.oryxel.viabedrockutility.payload.impl.entity;

import lombok.Getter;
import org.oryxel.viabedrockutility.enums.bedrock.ActorFlags;
import org.oryxel.viabedrockutility.payload.BasePayload;

import java.util.Set;
import java.util.UUID;

@Getter
public class ModelRequestPayload extends BasePayload {
    private final String identifier;
    private final UUID uuid;
    private final EntityData entityData;

    public ModelRequestPayload(String identifier, Set<ActorFlags> flags, Integer variant, Integer mark_variant, Integer skinId, Float scale, UUID uuid) {
        this.identifier = identifier;
        this.uuid = uuid;

        this.entityData = new EntityData(flags, variant, mark_variant, skinId, scale);
    }

    public record EntityData(Set<ActorFlags> flags, Integer variant, Integer mark_variant, Integer skinId, Float scale) {
    }
}
