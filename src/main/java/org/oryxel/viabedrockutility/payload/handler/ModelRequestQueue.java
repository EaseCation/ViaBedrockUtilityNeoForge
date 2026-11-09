package org.oryxel.viabedrockutility.payload.handler;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;

/** Client-thread model work. Adjacent snapshots coalesce; animation commands are ordering barriers. */
final class ModelRequestQueue<S, A> {
    private final int maxEntities, maxOperations, maxPerEntity;
    private final Map<UUID, Lane> lanes = new LinkedHashMap<>();
    private final LinkedHashSet<UUID> pending = new LinkedHashSet<>();
    private int operationCount;
    private long generation, received, duplicate, coalesced, rejected, applied, animations, failed, cancelled;
    private long lastDrainNanos, maxDrainNanos, overBudgetFrames;
    private boolean draining;

    ModelRequestQueue(int maxEntities, int maxOperations, int maxPerEntity) {
        if (maxEntities < 1 || maxOperations < maxEntities || maxPerEntity < 1) {
            throw new IllegalArgumentException("Invalid model queue limits");
        }
        this.maxEntities = maxEntities;
        this.maxOperations = maxOperations;
        this.maxPerEntity = maxPerEntity;
    }

    boolean model(UUID id, S state) {
        Objects.requireNonNull(id); Objects.requireNonNull(state);
        received++;
        Lane lane = lanes.get(id);
        if (lane != null && state.equals(lane.latest)) { duplicate++; return true; }
        if (lane == null) {
            if (lanes.size() >= maxEntities || operationCount >= maxOperations) { rejected++; return false; }
            lane = new Lane();
            lanes.put(id, lane);
        }
        Operation tail = lane.work.peekLast();
        if (tail != null && tail.model) {
            lane.work.removeLast();
            lane.work.addLast(new Operation(state, null));
            lane.latest = state;
            coalesced++;
            return true;
        }
        if (operationCount >= maxOperations || lane.work.size() >= maxPerEntity) { rejected++; return false; }
        lane.latest = state;
        lane.work.addLast(new Operation(state, null));
        operationCount++;
        pending.add(id);
        return true;
    }

    boolean animation(UUID id, A command) {
        Objects.requireNonNull(command);
        Lane lane = lanes.get(id);
        if (lane == null || operationCount >= maxOperations || lane.work.size() >= maxPerEntity) { rejected++; return false; }
        lane.work.addLast(new Operation(null, command));
        operationCount++;
        pending.add(id);
        return true;
    }

    boolean contains(UUID id) { return lanes.containsKey(id); }

    void drain(int maxCount, long maxNanos, LongSupplier clock, BiPredicate<UUID, S> apply,
               BiConsumer<UUID, A> animate, BiConsumer<UUID, RuntimeException> errors) {
        if (draining || maxCount < 1 || maxNanos < 1) return;
        draining = true;
        long start = clock.getAsLong();
        long epoch = generation;
        int processed = 0;
        try {
            while (!pending.isEmpty() && processed < maxCount && generation == epoch) {
                if (processed > 0 && clock.getAsLong() - start >= maxNanos) break;
                UUID id = pending.iterator().next();
                pending.remove(id);
                Lane lane = lanes.get(id);
                Operation operation = lane.work.removeFirst();
                operationCount--;
                processed++;
                try {
                    if (operation.model) {
                        boolean success = apply.test(id, operation.state);
                        if (lanes.get(id) == lane && generation == epoch) lane.ready = success;
                        if (success) applied++; else failed++;
                    } else if (lane.ready) {
                        animate.accept(id, operation.animation);
                        animations++;
                    } else {
                        cancelled++;
                    }
                } catch (RuntimeException failure) {
                    if (operation.model) lane.ready = false;
                    failed++;
                    try { errors.accept(id, failure); } catch (RuntimeException ignored) { /* Continue other entities. */ }
                }
                if (lanes.get(id) == lane && generation == epoch && !lane.work.isEmpty()) pending.add(id);
            }
        } finally {
            lastDrainNanos = Math.max(0, clock.getAsLong() - start);
            maxDrainNanos = Math.max(maxDrainNanos, lastDrainNanos);
            if (lastDrainNanos > maxNanos) overBudgetFrames++;
            draining = false;
        }
    }

    void forget(UUID id) {
        Lane lane = lanes.remove(id);
        pending.remove(id);
        if (lane != null) { operationCount -= lane.work.size(); cancelled += lane.work.size(); }
    }

    /** Resource generations invalidate old animations; rebuild the latest snapshot under the same budget. */
    void rebuild() {
        generation++;
        cancelled += operationCount;
        pending.clear();
        operationCount = 0;
        lanes.forEach((id, lane) -> {
            lane.work.clear(); lane.ready = false;
            lane.work.addLast(new Operation(lane.latest, null));
            operationCount++; pending.add(id);
        });
    }

    void clear() {
        generation++;
        cancelled += operationCount;
        lanes.clear(); pending.clear(); operationCount = 0;
    }

    Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entities", lanes.size()); result.put("pendingEntities", pending.size());
        result.put("pendingOperations", operationCount); result.put("received", received);
        result.put("duplicates", duplicate); result.put("coalesced", coalesced); result.put("rejected", rejected);
        result.put("applied", applied); result.put("animations", animations); result.put("failed", failed);
        result.put("cancelled", cancelled); result.put("lastDrainNanos", lastDrainNanos);
        result.put("maxDrainNanos", maxDrainNanos); result.put("overBudgetFrames", overBudgetFrames);
        result.put("maxEntities", maxEntities); result.put("maxOperations", maxOperations);
        result.put("maxPerEntity", maxPerEntity);
        return result;
    }

    private final class Lane {
        S latest;
        boolean ready;
        final ArrayDeque<Operation> work = new ArrayDeque<>();
    }
    private final class Operation {
        final boolean model;
        final S state;
        final A animation;
        Operation(S state, A animation) { this.state = state; this.animation = animation; this.model = state != null; }
    }
}
