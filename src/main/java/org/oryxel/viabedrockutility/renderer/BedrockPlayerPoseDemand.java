package org.oryxel.viabedrockutility.renderer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived demand gate for expensive world-space player skeleton snapshots. */
public final class BedrockPlayerPoseDemand {
    private static final long CAPTURE_GRACE_TICKS = 1L;
    private final Map<UUID, Long> requestedThroughTick = new ConcurrentHashMap<>();

    public void request(UUID ownerUuid, long currentTick) {
        if (ownerUuid == null || currentTick == Long.MIN_VALUE) {
            return;
        }
        final long requestedThrough = currentTick + CAPTURE_GRACE_TICKS;
        requestedThroughTick.merge(ownerUuid, requestedThrough, Math::max);
    }

    public boolean isRequested(UUID ownerUuid, long currentTick) {
        if (ownerUuid == null || currentTick == Long.MIN_VALUE) {
            return false;
        }
        final Long requestedThrough = requestedThroughTick.get(ownerUuid);
        return requestedThrough != null && requestedThrough >= currentTick;
    }

    public void prune(long currentTick) {
        if (currentTick == Long.MIN_VALUE) {
            return;
        }
        requestedThroughTick.entrySet().removeIf(entry -> entry.getValue() < currentTick);
    }

    public void clear() {
        requestedThroughTick.clear();
    }

    int size() {
        return requestedThroughTick.size();
    }
}
