package org.oryxel.viabedrockutility.payload.impl.player;

import lombok.Getter;
import org.oryxel.viabedrockutility.payload.BasePayload;

import java.util.UUID;

@Getter
public final class PlayerVisualStatePayload extends BasePayload {
    public static final int CUSTOM_SPECTATOR = 1;
    public static final int KNOWN_FLAGS = CUSTOM_SPECTATOR;

    private final UUID playerUuid;
    private final int flags;

    public PlayerVisualStatePayload(final UUID playerUuid, final int flags) {
        if (playerUuid == null) {
            throw new IllegalArgumentException("Player UUID cannot be null");
        }
        if ((flags & ~KNOWN_FLAGS) != 0) {
            throw new IllegalArgumentException("Unknown player visual state flags: " + flags);
        }
        this.playerUuid = playerUuid;
        this.flags = flags;
    }
}
