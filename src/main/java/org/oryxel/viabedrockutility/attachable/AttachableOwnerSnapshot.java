package org.oryxel.viabedrockutility.attachable;

import java.util.UUID;

public record AttachableOwnerSnapshot(UUID uuid, String identifier, float attackTime,
                                      float targetXRotation, float targetYRotation) {
    public static final AttachableOwnerSnapshot EMPTY =
            new AttachableOwnerSnapshot(null, "", 0.0F, 0.0F, 0.0F);

    public AttachableOwnerSnapshot(UUID uuid, String identifier, float attackTime) {
        this(uuid, identifier, attackTime, 0.0F, 0.0F);
    }
}
