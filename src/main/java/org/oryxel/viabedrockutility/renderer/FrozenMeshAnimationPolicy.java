package org.oryxel.viabedrockutility.renderer;

final class FrozenMeshAnimationPolicy {
    static final long QUERY_REBAKE_STABLE_NANOS = 100_000_000L;
    static final long MIN_EXPLICIT_TIMEOUT_NANOS = 10_000_000_000L;
    static final long MAX_EXPLICIT_TIMEOUT_NANOS = 300_000_000_000L;

    private FrozenMeshAnimationPolicy() {
    }

    static boolean queryRebakePending(long notBeforeNanos, long nowNanos) {
        return notBeforeNanos != 0L && nowNanos < notBeforeNanos;
    }

    static long explicitTimeoutNanos(float durationSeconds) {
        long requested = (long) ((durationSeconds * 2.0D + 5.0D) * 1_000_000_000.0D);
        return Math.max(MIN_EXPLICIT_TIMEOUT_NANOS,
                Math.min(MAX_EXPLICIT_TIMEOUT_NANOS, requested));
    }

    static boolean shouldReleaseExplicit(boolean looping, boolean zeroDuration, int renderedFrames,
                                         boolean donePlaying, boolean timedOut,
                                         boolean freezeAfterCompletedCycle) {
        if (zeroDuration && renderedFrames >= 1) {
            return true;
        }
        boolean mayFreeze = !looping || freezeAfterCompletedCycle;
        return mayFreeze && (donePlaying || timedOut);
    }
}
