package org.oryxel.viabedrockutility.attachable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Owner+logical-hand registry. Mutable runtime state is never shared between owners. */
public final class AttachableRuntimeRegistry<T> {
    private final Map<RuntimeKey, Entry<T>> entries = new HashMap<>();
    private final java.util.function.Consumer<T> disposer;
    public AttachableRuntimeRegistry() { this(ignored -> {}); }
    public AttachableRuntimeRegistry(java.util.function.Consumer<T> disposer) { this.disposer = java.util.Objects.requireNonNull(disposer); }

    public synchronized T getOrCreate(RuntimeKey key, RuntimeIdentity identity, long tick,
                                      Supplier<T> factory) {
        Entry<T> entry = entries.get(key);
        if (entry == null || !entry.identity().equals(identity)) {
            T replacement = factory.get();
            if (entry != null) disposer.accept(entry.runtime());
            entry = new Entry<>(identity, replacement, tick);
            entries.put(key, entry);
        } else {
            entry = new Entry<>(entry.identity(), entry.runtime(), tick);
            entries.put(key, entry);
        }
        return entry.runtime();
    }

    /** A stale tick snapshot must never retire a replacement registered for the same hand. */
    public synchronized boolean removeIfSame(RuntimeKey key, T expectedRuntime) {
        Entry<T> entry = entries.get(key);
        if (entry == null || entry.runtime() != expectedRuntime) return false;
        disposer.accept(entry.runtime());
        entries.remove(key);
        return true;
    }

    public synchronized void evictOlderThan(long minimumTick) {
        Iterator<Entry<T>> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry<T> entry = iterator.next();
            if (entry.lastSeenTick() < minimumTick) {
                disposer.accept(entry.runtime());
                iterator.remove();
            }
        }
    }

    public synchronized void clear() {
        entries.values().forEach(entry -> disposer.accept(entry.runtime()));
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
