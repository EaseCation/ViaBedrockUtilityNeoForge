package org.oryxel.viabedrockutility.payload.handler;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class ModelRequestQueueTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);
    private final List<String> work = new ArrayList<>();
    private final AtomicLong clock = new AtomicLong();
    private ModelRequestQueue<String, String> queue(int entities, int total, int perEntity) {
        return new ModelRequestQueue<>(entities, total, perEntity);
    }
    private void drain(ModelRequestQueue<String, String> q, int count, long budget, long each) {
        q.drain(count, budget, clock::get, (id, state) -> { work.add("model:" + id.getLeastSignificantBits() + ":" + state); clock.addAndGet(each); return true; },
                (id, animation) -> { work.add("animation:" + animation); clock.addAndGet(each); }, (id, e) -> fail(e));
    }
    private long metric(ModelRequestQueue<?, ?> q, String key) { return ((Number) q.snapshot().get(key)).longValue(); }

    @Test void hundredThousandDuplicatesDoNotBuildOrGrowQueue() {
        var q = queue(2, 8, 4);
        for (int i = 0; i < 100_000; i++) assertTrue(q.model(A, "same"));
        assertEquals(1, metric(q, "pendingOperations")); assertTrue(work.isEmpty());
        drain(q, 4, 20, 1);
        assertEquals(List.of("model:1:same"), work);
        assertTrue(q.model(A, "same")); assertEquals(0, metric(q, "pendingOperations"));
        assertEquals(100_000, metric(q, "duplicates"));
    }
    @Test void changingSnapshotsCoalesceWithoutAnimations() {
        var q = queue(2, 8, 4);
        for (int i = 0; i < 10_000; i++) q.model(A, "state" + i);
        drain(q, 4, 20, 1);
        assertEquals(List.of("model:1:state9999"), work);
        assertEquals(9999, metric(q, "coalesced"));
    }
    @Test void animationIsAnOrderingBarrier() {
        var q = queue(2, 8, 4);
        q.model(A, "before"); q.animation(A, "walk"); q.model(A, "after"); q.model(A, "latest");
        drain(q, 4, 20, 1);
        assertEquals(List.of("model:1:before", "animation:walk", "model:1:latest"), work);
    }
    @Test void modelBurstObeysCountBudgetAndEventuallyFinishes() {
        var q = queue(128, 256, 8);
        for (int i = 0; i < 108; i++) q.model(new UUID(0, i), "state");
        drain(q, 4, 100, 1); assertEquals(4, work.size());
        for (int i = 0; i < 26; i++) drain(q, 4, 100, 1);
        assertEquals(108, work.size()); assertEquals(0, metric(q, "pendingOperations"));
    }
    @Test void firstOversizedOperationProgressesButStopsTheFrame() {
        var q = queue(2, 8, 4);q.model(A, "a");q.model(B, "b");
        drain(q, 4, 2, 3); assertEquals(1, work.size()); assertEquals(1, metric(q, "overBudgetFrames"));
        drain(q, 4, 2, 1); assertEquals(2, work.size());
    }
    @Test void entitiesHaveFairTurns() {
        var q = queue(2, 8, 4);q.model(A, "a");q.animation(A, "1");q.animation(A, "2");q.model(B, "b");
        drain(q, 2, 100, 1);assertEquals(List.of("model:1:a", "model:2:b"), work);
    }
    @Test void entityAndOperationLimitsRejectWithoutEvictingActiveState() {
        var q = queue(2, 3, 2);assertTrue(q.model(A, "a"));assertTrue(q.model(B, "b"));
        assertFalse(q.model(C, "c"));assertTrue(q.animation(A, "animate"));assertFalse(q.animation(B, "overflow"));
        assertFalse(q.model(A, "new"));assertTrue(q.model(B, "latest"));
        assertEquals(3, metric(q, "pendingOperations"));assertEquals(2, metric(q, "entities"));
        drain(q, 8, 100, 1);assertEquals(List.of("model:1:a", "model:2:latest", "animation:animate"), work);
    }
    @Test void removalAndSameUuidReuseCannotReplayOldWork() {
        var q = queue(2, 8, 4);q.model(A, "old");q.animation(A, "old");q.forget(A);q.model(A, "new");
        drain(q, 8, 100, 1);assertEquals(List.of("model:1:new"), work);
    }
    @Test void reloadRebuildsLatestStatesAndDropsOldAnimationCommands() {
        var q = queue(2, 8, 4);q.model(A, "a");drain(q, 4, 20, 1);work.clear();
        q.animation(A, "old");q.model(A, "b");q.rebuild();
        drain(q, 8, 100, 1);assertEquals(List.of("model:1:b"), work);
    }
    @Test void clearDuringApplyDoesNotPublishReadyOrDrainNewGeneration() {
        var q = queue(2, 8, 4);q.model(A, "old");q.model(B, "old");
        q.drain(8, 100, clock::get, (id, state) -> { q.clear();q.model(A, "new");return true; }, (id,a)->fail(), (id,e)->fail(e));
        assertEquals(1, metric(q,"pendingOperations"));drain(q,8,100,1);assertEquals(List.of("model:1:new"), work);
    }
    @Test void callbackMayRemoveAndReplaceSameUuid() {
        var q = queue(2, 8, 4);q.model(A, "old");q.animation(A, "old");
        q.drain(1,100,clock::get,(id,state)->{q.forget(id);q.model(id,"new");return true;},(id,a)->fail(),(id,e)->fail(e));
        drain(q,8,100,1);assertEquals(List.of("model:1:new"), work);
    }
    @Test void failedModelDoesNotAutoRetryOrPlayItsAnimation() {
        var q = queue(2, 8, 4);q.model(A, "bad");q.animation(A, "bad");q.model(B, "good");
        q.drain(8,100,clock::get,(id,state)->{if(id.equals(A))throw new IllegalStateException("bad");work.add(state);return true;},
                (id,a)->fail(),(id,e)->{throw new IllegalStateException("error callback");});
        assertEquals(List.of("good"),work);q.model(A,"bad");assertEquals(0,metric(q,"pendingOperations"));
        q.model(A,"repaired");drain(q,8,100,1);assertEquals(1,metric(q,"failed"));
    }
    @Test void animationFailureDoesNotPreventLaterStateOrOtherEntities() {
        var q=queue(2,8,4);q.model(A,"a");drain(q,1,100,1);work.clear();q.animation(A,"bad");q.model(A,"new");q.model(B,"b");
        q.drain(8,100,clock::get,(id,state)->{work.add(state);return true;},(id,a)->{throw new IllegalStateException();},(id,e)->{});
        assertEquals(List.of("b","new"),work);assertEquals(1,metric(q,"failed"));
    }
    @Test void reentrantDrainIsIgnored() {
        var q=queue(2,8,4);q.model(A,"a");q.model(B,"b");
        q.drain(1,100,clock::get,(id,state)->{drain(q,8,100,1);return true;},(id,a)->fail(),(id,e)->fail());
        assertTrue(work.isEmpty());assertEquals(1,metric(q,"pendingOperations"));
    }
}
