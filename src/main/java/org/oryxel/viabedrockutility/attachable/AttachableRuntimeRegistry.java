package org.oryxel.viabedrockutility.attachable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Owner+logical-hand registry. Mutable runtime state is never shared between owners. */
public final class AttachableRuntimeRegistry<T> {
    private final Map<RuntimeKey, Entry<T>> entries = new HashMap<>();

    public synchronized T getOrCreate(RuntimeKey key, RuntimeIdentity identity, long tick,
                                      Supplier<T> factory) {
        Entry<T> entry = entries.get(key);
        if (entry == null || !entry.identity().equals(identity)) {
            entry = new Entry<>(identity, factory.get(), tick);
            entries.put(key, entry);
        } else {
            entry = new Entry<>(entry.identity(), entry.runtime(), tick);
            entries.put(key, entry);
        }
        return entry.runtime();
    }

    public synchronized void evictOlderThan(long minimumTick) {
        Iterator<Entry<T>> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().lastSeenTick() < minimumTick) {
                iterator.remove();
            }
        }
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized Map<RuntimeKey, EntryView<T>> snapshot() {
        final Map<RuntimeKey, EntryView<T>> result = new HashMap<>();
        entries.forEach((key, entry) -> result.put(key,
                new EntryView<>(entry.identity(), entry.runtime(), entry.lastSeenTick())));
        return Map.copyOf(result);
    }

    public record RuntimeKey(UUID ownerUuid, AttachableQueryContext.LogicalHand hand) {
    }

    /** Deliberately excludes NBT/components so ammo updates do not restart controllers. */
    public record RuntimeIdentity(String itemIdentifier, String attachableIdentifier, long packGeneration) {
    }

    public record EntryView<T>(RuntimeIdentity identity, T runtime, long lastSeenTick) {
    }

    private record Entry<T>(RuntimeIdentity identity, T runtime, long lastSeenTick) {
    }
}
