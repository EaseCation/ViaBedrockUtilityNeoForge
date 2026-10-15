package org.oryxel.viabedrockutility.payload.impl.player;

import lombok.Getter;
import org.oryxel.viabedrockutility.payload.BasePayload;

@Getter
public final class InputLocksPayload extends BasePayload {
    public static final int CAMERA = 1 << 1;
    public static final int MOVEMENT = 1 << 2;
    public static final int SUPPORTED_FLAGS = CAMERA | MOVEMENT;

    private final int flags;

    public InputLocksPayload(final int flags) {
        // Older proxies forward all Bedrock permission bits; v1 only implements these two locks.
        this.flags = flags & SUPPORTED_FLAGS;
    }
}
