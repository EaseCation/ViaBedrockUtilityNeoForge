package org.oryxel.viabedrockutility.attachable;

/** Monotonic client-tick counter scoped to the current client level identity. */
final class AttachableClientTickCounter {
    private Object levelIdentity;
    private long tick;

    Snapshot synchronize(Object currentLevel) {
        if (currentLevel == null) {
            final boolean changed = levelIdentity != null;
            levelIdentity = null;
            tick = 0L;
            return new Snapshot(tick, changed);
        }
        if (currentLevel != levelIdentity) {
            levelIdentity = currentLevel;
            tick = 0L;
            return new Snapshot(tick, true);
        }
        return new Snapshot(tick, false);
    }

    Snapshot advance(Object currentLevel) {
        final Snapshot synchronizedTick = synchronize(currentLevel);
        if (currentLevel != null && !synchronizedTick.levelChanged()) {
            tick++;
        }
        return new Snapshot(tick, synchronizedTick.levelChanged());
    }

    record Snapshot(long tick, boolean levelChanged) {
    }
}
