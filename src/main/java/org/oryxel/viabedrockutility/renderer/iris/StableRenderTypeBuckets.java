package org.oryxel.viabedrockutility.renderer.iris;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class StableRenderTypeBuckets<T> {
    private final List<LinkedHashSet<T>> buckets;

    StableRenderTypeBuckets(int bucketCount) {
        this.buckets = new ArrayList<>(bucketCount);
        for (int i = 0; i < bucketCount; i++) {
            this.buckets.add(new LinkedHashSet<>());
        }
    }

    void add(int bucket, T value) {
        this.buckets.get(bucket).add(value);
    }

    void clear(int bucket) {
        this.buckets.get(bucket).clear();
    }

    void clear() {
        for (LinkedHashSet<T> bucket : this.buckets) {
            bucket.clear();
        }
    }

    List<T> ordered() {
        final List<T> order = new ArrayList<>();
        for (LinkedHashSet<T> bucket : this.buckets) {
            order.addAll(bucket);
        }
        return order;
    }
}
