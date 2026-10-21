package org.oryxel.viabedrockutility.payload.impl.player;

import org.oryxel.viabedrockutility.payload.BasePayload;

/** 权限锁与NOAI定身采用同一份快照，避免一方解除覆盖另一方。 */
public final class InputLocksV2Payload extends BasePayload {
    private final int flags;
    private final boolean immobile;

    public InputLocksV2Payload(int flags, boolean immobile) {
        this.flags = flags;
        this.immobile = immobile;
    }

    public int getFlags() {
        return this.flags;
    }

    public boolean isImmobile() {
        return this.immobile;
    }
}
