package org.oryxel.viabedrockutility.payload.impl.particle;

import lombok.Getter;
import org.oryxel.viabedrockutility.payload.BasePayload;

import java.util.UUID;

@Getter
public final class SpawnParticleV2Payload extends BasePayload {
    public static final int WORLD_ANCHOR = 0;
    public static final int ENTITY_ANCHOR = 1;
    private final String identifier;
    private final int anchorKind;
    private final UUID ownerUuid;
    private final float x, y, z;
    private final String molangVarsJson;

    public SpawnParticleV2Payload(String identifier, int anchorKind, UUID ownerUuid,
                                  float x, float y, float z, String molangVarsJson) {
        if (anchorKind != WORLD_ANCHOR && anchorKind != ENTITY_ANCHOR) {
            throw new IllegalArgumentException("Unsupported particle anchor kind: " + anchorKind);
        }
        if ((anchorKind == WORLD_ANCHOR) != (ownerUuid == null)) {
            throw new IllegalArgumentException("Particle anchor kind and owner UUID are inconsistent");
        }
        this.identifier = identifier;
        this.anchorKind = anchorKind;
        this.ownerUuid = ownerUuid;
        this.x = x;
        this.y = y;
        this.z = z;
        this.molangVarsJson = molangVarsJson;
    }
}
