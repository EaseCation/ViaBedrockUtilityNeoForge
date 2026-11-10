package org.oryxel.viabedrockutility.payload;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class SkinWorkQueueTest {
    static class Work implements SkinWorkQueue.Work {
        int steps, closes; boolean ready = true; int duration = 3;
        public boolean ready() { return ready; }
        public boolean step() { return ++steps == duration; }
        public void close() { assertEquals(0, closes++); }
    }
    @Test void splitsOneSkinAcrossFramesAndPreservesItsOverlayDependency() {
        var q = new SkinWorkQueue(10, 8, 100); var id = UUID.randomUUID();
        var base = new Work(); var overlay = new Work(); overlay.duration = 1;
        q.offer(id, 20, base, true); q.offer(id, 10, overlay, false);
        for (int i = 1; i <= 3; i++) {
            q.pump(100, 100, () -> 0); assertEquals(i, base.steps); assertEquals(0, overlay.steps);
        }
        assertEquals(1, base.closes); assertEquals(10, q.reservedBytes());
        q.pump(100, 100, () -> 0); assertEquals(1, overlay.steps); assertEquals(0, q.reservedBytes());
    }
    @Test void enforcesStageTimeAndCapacityWithoutStarvingLaterPlayers() {
        var q = new SkinWorkQueue(3, 2, 60); var clock = new AtomicLong(); var work = new ArrayList<Work>();
        for (int i = 0; i < 3; i++) {
            var w = new Work() { public boolean step() { clock.addAndGet(3); return super.step(); } };
            work.add(w); assertTrue(q.offer(UUID.randomUUID(), 20, w, false));
        }
        var refused = new Work(); assertFalse(q.offer(UUID.randomUUID(), 1, refused, false));
        assertEquals(1, refused.closes); assertEquals(0, refused.steps);
        for (int i = 0; i < 3; i++) q.pump(2, 2, clock::get);
        assertTrue(work.stream().allMatch(w -> w.steps == 1));
        q.clear(); assertEquals(0, q.reservedBytes()); assertTrue(work.stream().allMatch(w -> w.closes == 1));
    }
    @Test void waitingAssemblyDoesNotBlockOtherPlayersAndCancellationClosesPreparedWorkOnce() {
        var q = new SkinWorkQueue(4, 2, 100); var id = UUID.randomUUID();
        var waiting = new Work(); waiting.ready = false; var other = new Work();
        q.offer(id, 20, waiting, false); q.offer(UUID.randomUUID(), 20, other, false);
        q.pump(2, 10, () -> 0); assertEquals(0, waiting.steps); assertEquals(1, other.steps);
        q.cancel(id); q.cancel(id); assertEquals(1, waiting.closes);
        q.clear(); assertEquals(1, other.closes); assertEquals(0, q.pendingJobs());
    }
    @Test void failureCanCancelItsDependenciesAndLeaveReplacementAndOtherPlayersAlive() {
        var q = new SkinWorkQueue(8, 4, 100); var id = UUID.randomUUID(); var replacement = new Work();
        var bad = new Work() {
            public boolean step() { throw new IllegalStateException("preparation"); }
            public void failed(RuntimeException e) { q.cancel(id); q.offer(id, 5, replacement, false); }
        };
        var dependent = new Work(); var other = new Work();
        q.offer(id, 20, bad, false); q.offer(id, 20, dependent, false); q.offer(UUID.randomUUID(), 20, other, false);
        q.pump(2, 10, () -> 0);
        assertEquals(1, bad.closes); assertEquals(1, dependent.closes); assertEquals(0, replacement.closes);
        assertEquals(1, other.steps); assertEquals(25, q.reservedBytes()); q.clear();
    }
    @Test void assemblyReplacementRejectsInconsistentInputAndCountsDuplicateChunksOnce() {
        var info = new PayloadHandler.SkinInfo("", "", 2, 1, 2, 1);
        assertTrue(info.setData(new byte[4], 1)); assertTrue(info.setData(new byte[4], 1)); assertFalse(info.isComplete());
        assertTrue(info.setData(new byte[4], 0)); assertTrue(info.isComplete()); assertEquals(8, info.getData().length);
        assertFalse(info.setData(new byte[5], 0)); assertFalse(info.setData(new byte[4], 2));
        assertThrows(IllegalArgumentException.class, () -> SkinImageBudget.reservation(1, 1, 0, "", ""));
    }
}
