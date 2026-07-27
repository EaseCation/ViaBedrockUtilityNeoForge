package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.VertexFormat;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FrozenEntityMeshCacheTest {
    @Test
    void enforcesEntryAndByteLimitsWithAccessOrderLru() {
        FrozenEntityMeshCache cache = new FrozenEntityMeshCache(2, () -> 100L);
        TestHandle firstHandle = new TestHandle(40);
        FrozenMeshEntry first = entry(firstHandle);
        FrozenMeshEntry second = entry(new TestHandle(40));
        FrozenMeshEntry third = entry(new TestHandle(40));

        assertTrue(cache.add(first));
        assertTrue(cache.add(second));
        assertTrue(cache.touch(first));
        assertTrue(cache.add(third));

        assertTrue(first.isValid(), "recently touched entry must survive");
        assertFalse(second.isValid(), "least-recently-used entry must be evicted");
        assertTrue(third.isValid());
        assertEquals(2, cache.size());
        assertEquals(80, cache.bytes());
    }

    @Test
    void rejectsOversizedEntryAndReleaseIsIdempotent() {
        FrozenEntityMeshCache cache = new FrozenEntityMeshCache(256, () -> 64L);
        TestHandle handle = new TestHandle(65);
        FrozenMeshEntry entry = entry(handle);

        assertFalse(cache.add(entry));
        entry.close();
        assertEquals(1, handle.closes.get());
        assertEquals(0, cache.size());
        assertEquals(0, cache.bytes());
    }

    @Test
    void enforcesTheProductionEntryCapIndependentlyOfBytes() {
        FrozenEntityMeshCache cache = new FrozenEntityMeshCache(256, () -> Long.MAX_VALUE);
        FrozenMeshEntry first = entry(new TestHandle(1));
        assertTrue(cache.add(first));
        for (int i = 1; i <= 256; i++) {
            assertTrue(cache.add(entry(new TestHandle(1))));
        }

        assertEquals(256, cache.size());
        assertFalse(first.isValid());
    }

    @Test
    void byteLimitEvictsEvenWhenEntrySlotsRemain() {
        FrozenEntityMeshCache cache = new FrozenEntityMeshCache(256, () -> 100L);
        FrozenMeshEntry first = entry(new TestHandle(40));
        assertTrue(cache.add(first));
        assertTrue(cache.add(entry(new TestHandle(40))));
        assertTrue(cache.add(entry(new TestHandle(40))));

        assertFalse(first.isValid());
        assertEquals(2, cache.size());
        assertEquals(80, cache.bytes());
    }

    private static FrozenMeshEntry entry(TestHandle handle) {
        return new FrozenMeshEntry(handle, null, 128, 192, VertexFormat.Mode.QUADS, 0);
    }

    private static final class TestHandle implements FrozenMeshBackend.Handle {
        private final long bytes;
        private final AtomicInteger closes = new AtomicInteger();

        private TestHandle(long bytes) {
            this.bytes = bytes;
        }

        @Override public long sizeBytes() { return bytes; }
        @Override public boolean isClosed() { return closes.get() > 0; }
        @Override public void close() { closes.compareAndSet(0, 1); }
    }
}
