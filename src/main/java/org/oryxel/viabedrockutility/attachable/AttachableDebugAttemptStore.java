package org.oryxel.viabedrockutility.attachable;

import org.oryxel.viabedrockutility.attachable.AttachableDebugLog.DebugAttempt;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded-by-time diagnostics; runtime behavior never depends on entries in this store. */
final class AttachableDebugAttemptStore {
    private final Map<AttachableRuntimeRegistry.RuntimeKey, TimedAttempt> attempts = new ConcurrentHashMap<>();

    DebugAttempt record(DebugAttempt attempt, long tick) {
        final TimedAttempt previous = attempts.put(attempt.runtimeKey(), new TimedAttempt(attempt, tick));
        return previous == null ? null : previous.attempt();
    }

    void evictOlderThan(long oldestRetainedTick) {
        attempts.entrySet().removeIf(entry -> entry.getValue().lastSeenTick() < oldestRetainedTick);
    }

    List<DebugAttempt> snapshot() {
        return attempts.values().stream()
                .map(TimedAttempt::attempt)
                .sorted(java.util.Comparator.comparing(attempt -> attempt.runtimeKey().toString()))
                .toList();
    }

    int size() {
        return attempts.size();
    }

    void clear() {
        attempts.clear();
    }

    private record TimedAttempt(DebugAttempt attempt, long lastSeenTick) {
    }
}
