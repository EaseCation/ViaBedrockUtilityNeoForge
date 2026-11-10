package org.oryxel.viabedrockutility.payload;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Render-thread owned. One stage per player per frame; player-local dependencies stay FIFO. */
final class SkinWorkQueue {
    interface Work extends AutoCloseable {
        /** False defers without spending a stage (for example, awaiting base-skin assembly). */
        default boolean ready() { return true; }
        boolean step(); // true when published or deliberately discarded
        default void failed(RuntimeException failure) { }
        @Override void close();
    }
    private static final class Entry {
        final long bytes;
        final Work work;
        boolean closed;
        Entry(long bytes, Work work) { this.bytes = bytes; this.work = work; }
    }
    private final Map<UUID, ArrayDeque<Entry>> players = new LinkedHashMap<>();
    private final int maximumJobs, maximumPerPlayer;
    private final long maximumBytes;
    private long bytes, rejected, completed, failed, overBudget;
    private int jobs;

    SkinWorkQueue(int maximumJobs, int maximumPerPlayer, long maximumBytes) {
        if (maximumJobs < 1 || maximumPerPlayer < 1 || maximumBytes < 1) throw new IllegalArgumentException();
        this.maximumJobs = maximumJobs; this.maximumPerPlayer = maximumPerPlayer; this.maximumBytes = maximumBytes;
    }
    boolean offer(UUID player, long reservation, Work work, boolean first) {
        var pending = players.get(player);
        if (reservation < 0 || reservation > maximumBytes - bytes || jobs >= maximumJobs
                || pending != null && pending.size() >= maximumPerPlayer) {
            rejected++; work.close(); return false;
        }
        if (pending == null) { pending = new ArrayDeque<>(); players.put(player, pending); }
        var entry = new Entry(reservation, work);
        if (first) pending.addFirst(entry); else pending.addLast(entry);
        bytes += reservation; jobs++;
        return true;
    }
    void pump(int maximumSteps, long nanos, LongSupplier clock) {
        long start = clock.getAsLong();
        int visits = players.size(), steps = 0;
        while (visits-- > 0 && !players.isEmpty() && steps < maximumSteps && clock.getAsLong() - start < nanos) {
            var iterator = players.entrySet().iterator();
            var pair = iterator.next(); UUID player = pair.getKey(); var pending = pair.getValue();
            iterator.remove(); players.put(player, pending); // Round robin; preserve the head while callbacks run.
            Entry entry = pending.peekFirst();
            try {
                if (!entry.work.ready()) continue;
                steps++;
                if (entry.work.step()) { completed++; finish(player, pending, entry); }
            } catch (RuntimeException failure) {
                failed++;
                try { entry.work.failed(failure); }
                finally { finish(player, pending, entry); }
            }
        }
        if (steps > 0 && clock.getAsLong() - start > nanos) overBudget++;
    }
    private void finish(UUID player, ArrayDeque<Entry> pending, Entry entry) {
        pending.remove(entry);
        if (pending.isEmpty()) players.remove(player, pending);
        release(entry);
    }
    private void release(Entry entry) {
        if (entry.closed) return;
        entry.closed = true; bytes -= entry.bytes; jobs--;
        entry.work.close();
    }
    void cancel(UUID player) {
        var pending = players.remove(player);
        if (pending != null) for (var entry : pending) release(entry);
    }
    void clear() { for (UUID player : players.keySet().toArray(UUID[]::new)) cancel(player); }
    int pendingJobs() { return jobs; }
    long reservedBytes() { return bytes; }
    Map<String, Object> statistics() {
        return Map.of("pending", jobs, "reservedBytes", bytes, "rejected", rejected,
                "completed", completed, "failed", failed, "overBudgetFrames", overBudget);
    }
}
