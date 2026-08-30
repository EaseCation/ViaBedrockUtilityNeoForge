package org.oryxel.viabedrockutility.animation;

import java.lang.ref.WeakReference;
import java.util.Objects;

/** Identifies the live Minecraft objects that own one player's animation controllers. */
public final class PlayerAnimationOwner {
    private final WeakReference<Object> playerInstance;
    private final WeakReference<Object> levelInstance;

    public PlayerAnimationOwner(Object playerInstance, Object levelInstance) {
        this.playerInstance = new WeakReference<>(Objects.requireNonNull(playerInstance, "playerInstance"));
        this.levelInstance = new WeakReference<>(Objects.requireNonNull(levelInstance, "levelInstance"));
    }

    public boolean hasSameInstances(PlayerAnimationOwner other) {
        if (other == null) {
            return false;
        }
        final Object otherPlayer = other.playerInstance.get();
        final Object otherLevel = other.levelInstance.get();
        return otherPlayer != null && otherLevel != null
                && playerInstance.get() == otherPlayer
                && levelInstance.get() == otherLevel;
    }
}
