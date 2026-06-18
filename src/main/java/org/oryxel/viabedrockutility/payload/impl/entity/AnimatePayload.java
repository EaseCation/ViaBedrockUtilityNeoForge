package org.oryxel.viabedrockutility.payload.impl.entity;

import lombok.Getter;
import org.oryxel.viabedrockutility.payload.BasePayload;

import java.util.UUID;

@Getter
public class AnimatePayload extends BasePayload {
    private final UUID entityUuid;
    private final String animationName;

    public AnimatePayload(UUID entityUuid, String animationName) {
        this.entityUuid = entityUuid;
        this.animationName = animationName;
    }
}
