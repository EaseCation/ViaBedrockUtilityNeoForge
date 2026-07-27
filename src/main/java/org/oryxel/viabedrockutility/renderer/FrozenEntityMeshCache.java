package org.oryxel.viabedrockutility.renderer;

import org.oryxel.viabedrockutility.config.LodConfig;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Render-thread access-order LRU for persistent entity vertex buffers. */
public final class FrozenEntityMeshCache {
    public static final int DEFAULT_MAX_ENTRIES = 256;
    private static final FrozenEntityMeshCache GLOBAL = new FrozenEntityMeshCache(
            DEFAULT_MAX_ENTRIES, () -> LodConfig.getInstance().getFrozenMeshMaxGpuBytes());

    private final int maxEntries;
    private final LongSupplier maxBytes;
    private final LinkedHashMap<FrozenMeshEntry, Boolean> entries = new LinkedHashMap<>(32, 0.75F, true);
    private long bytes;

    public FrozenEntityMeshCache(int maxEntries, LongSupplier maxBytes) {
        this.maxEntries = maxEntries;
        this.maxBytes = maxBytes;
    }

    public static FrozenEntityMeshCache global() {
        return GLOBAL;
    }

    public synchronized boolean add(FrozenMeshEntry entry) {
        long limit = Math.max(0L, maxBytes.getAsLong());
        if (entry.sizeBytes() > limit || maxEntries <= 0) {
            entry.close();
            return false;
        }
        evictUntilFits(entry.sizeBytes(), limit);
        if (entries.size() >= maxEntries || bytes + entry.sizeBytes() > limit) {
            entry.close();
            return false;
        }
        entries.put(entry, Boolean.TRUE);
        bytes += entry.sizeBytes();
        return true;
    }

    public synchronized boolean touch(FrozenMeshEntry entry) {
        if (entries.get(entry) == null || !entry.isValid()) {
            return false;
        }
        evictUntilFits(0L, Math.max(0L, maxBytes.getAsLong()));
        return entries.containsKey(entry) && entry.isValid();
    }

    public synchronized void remove(FrozenMeshEntry entry, String reason) {
        if (entries.remove(entry) != null) {
            bytes -= entry.sizeBytes();
        }
        entry.close();
        VbuRenderMetrics.recordFrozenInvalidation(reason);
    }

    public synchronized void invalidateAll(String reason) {
        for (FrozenMeshEntry entry : entries.keySet()) {
            entry.close();
        }
        if (!entries.isEmpty()) {
            VbuRenderMetrics.recordFrozenInvalidation(reason);
        }
        entries.clear();
        bytes = 0L;
    }

    public synchronized int size() { return entries.size(); }
    public synchronized long bytes() { return bytes; }

    private void evictUntilFits(long incoming, long limit) {
        Iterator<Map.Entry<FrozenMeshEntry, Boolean>> iterator = entries.entrySet().iterator();
        while ((entries.size() >= maxEntries || bytes + incoming > limit) && iterator.hasNext()) {
            FrozenMeshEntry eldest = iterator.next().getKey();
            iterator.remove();
            bytes -= eldest.sizeBytes();
            eldest.close();
            VbuRenderMetrics.recordFrozenInvalidation("lru");
        }
    }
}
