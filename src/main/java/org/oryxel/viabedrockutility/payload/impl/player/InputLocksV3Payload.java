package org.oryxel.viabedrockutility.payload.impl.player;

import org.oryxel.viabedrockutility.payload.BasePayload;

/** v3 在原子快照中增加禁跳和禁止下船，不把细粒度限制扩大成全部移动锁。 */
public final class InputLocksV3Payload extends BasePayload {
    public static final int JUMP = 1 << 6;
    public static final int DISMOUNT = 1 << 8;
    public static final int SUPPORTED_FLAGS = InputLocksPayload.SUPPORTED_FLAGS | JUMP | DISMOUNT;
    private final int flags;
    private final boolean immobile;

    public InputLocksV3Payload(int flags, boolean immobile) {
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
