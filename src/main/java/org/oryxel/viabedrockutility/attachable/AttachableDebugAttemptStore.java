package org.oryxel.viabedrockutility.attachable;

import org.oryxel.viabedrockutility.attachable.AttachableDebugLog.DebugAttempt;

import java.util.List;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded-by-time diagnostics; runtime behavior never depends on entries in this store. */
final class AttachableDebugAttemptStore {
    private static final int MAX_HISTORY = 128;
    private final Map<AttachableRuntimeRegistry.RuntimeKey, TimedAttempt> attempts = new ConcurrentHashMap<>();
    private final Deque<DebugAttempt> history = new ArrayDeque<>();

    synchronized DebugAttempt record(DebugAttempt attempt, long tick) {
        final TimedAttempt previous = attempts.put(attempt.runtimeKey(), new TimedAttempt(attempt, tick));
        if (previous == null || !sameState(previous.attempt(), attempt)) {
            history.addLast(attempt);
            while (history.size() > MAX_HISTORY) {
                history.removeFirst();
            }
        }
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

    synchronized List<DebugAttempt> historySnapshot() {
        return List.copyOf(history);
    }

    int size() {
        return attempts.size();
    }

    synchronized void clear() {
        attempts.clear();
        history.clear();
    }

    static boolean sameState(DebugAttempt first, DebugAttempt second) {
        return first.runtimeKey().equals(second.runtimeKey())
                && first.packGeneration() == second.packGeneration()
                && first.itemIdentifier().equals(second.itemIdentifier())
                && first.view() == second.view()
                && first.stage() == second.stage()
                && first.candidateCount() == second.candidateCount()
                && first.attachableIdentifier().equals(second.attachableIdentifier())
                && first.renderPasses().equals(second.renderPasses())
                && first.bindingBone().equals(second.bindingBone())
                && first.detail().equals(second.detail());
    }

    private record TimedAttempt(DebugAttempt attempt, long lastSeenTick) {
    }
}
