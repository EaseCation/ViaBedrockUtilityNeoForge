package org.oryxel.viabedrockutility.payload.impl.particle;

import lombok.Getter;
import org.oryxel.viabedrockutility.payload.BasePayload;

@Getter
public class SpawnParticlePayload extends BasePayload {
    private final String identifier;
    private final float x, y, z;
    private final String molangVarsJson;

    public SpawnParticlePayload(String identifier, float x, float y, float z, String molangVarsJson) {
        this.identifier = identifier;
        this.x = x;
        this.y = y;
        this.z = z;
        this.molangVarsJson = molangVarsJson;
    }
}
