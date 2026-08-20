package org.oryxel.viabedrockutility.payload.enums;

public enum PayloadType {
    CONFIRM, MODEL_REQUEST, ANIMATE,
    CAPE, SKIN_INFORMATION, SKIN_DATA,
    SKIN_ANIMATION_INFO, SKIN_ANIMATION_DATA,
    SPAWN_PARTICLE,
    /** V2 keeps SPAWN_PARTICLE wire compatibility and adds a host UUID/anchor kind. */
    SPAWN_PARTICLE_V2
}
