package org.oryxel.viabedrockutility.payload.impl.player;

import lombok.Getter;
import org.oryxel.viabedrockutility.payload.BasePayload;

@Getter
public final class InputLocksPayload extends BasePayload {
    public static final int CAMERA = 1 << 1;
    public static final int MOVEMENT = 1 << 2;

    private final int flags;

    public InputLocksPayload(final int flags) {
        this.flags = flags;
    }
}
