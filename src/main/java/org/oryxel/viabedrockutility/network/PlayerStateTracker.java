package org.oryxel.viabedrockutility.network;

import java.util.Objects;

public final class PlayerStateTracker {

    private Object playerIdentity;
    private Integer sentStateFlags;

    public Integer stateToSend(final Object playerIdentity, final boolean viaBedrockPresent, final int stateFlags) {
        if (this.playerIdentity != playerIdentity) {
            this.playerIdentity = playerIdentity;
            this.sentStateFlags = null;
        }
        if (playerIdentity == null || !viaBedrockPresent || Objects.equals(this.sentStateFlags, stateFlags)) {
            return null;
        }
        return stateFlags;
    }

    public void markSent(final Object playerIdentity, final int stateFlags) {
        if (this.playerIdentity == playerIdentity) {
            this.sentStateFlags = stateFlags;
        }
    }

    public void reset() {
        this.playerIdentity = null;
        this.sentStateFlags = null;
    }
}
