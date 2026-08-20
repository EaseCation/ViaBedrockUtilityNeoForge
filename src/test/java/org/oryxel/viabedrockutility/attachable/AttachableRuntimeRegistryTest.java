package org.oryxel.viabedrockutility.attachable;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class AttachableRuntimeRegistryTest {
    @Test
    void ammoChangesDoNotParticipateInRuntimeIdentity() {
        AttachableRuntimeRegistry<Object> registry = new AttachableRuntimeRegistry<>();
        var key = new AttachableRuntimeRegistry.RuntimeKey(UUID.randomUUID(),
                AttachableQueryContext.LogicalHand.MAIN_HAND);
        var identity = new AttachableRuntimeRegistry.RuntimeIdentity("test:gun", "test:gun", 1L);
        AtomicInteger creations = new AtomicInteger();

        Object first = registry.getOrCreate(key, identity, 1L, () -> new ObjectWithAmmo(creations.incrementAndGet(), 30));
        Object afterAmmoChange = registry.getOrCreate(key, identity, 2L,
                () -> new ObjectWithAmmo(creations.incrementAndGet(), 29));

        assertSame(first, afterAmmoChange);
        assertEquals(1, creations.get());
    }

    @Test
    void itemOrGenerationChangeReplacesAndTtlEvicts() {
        AttachableRuntimeRegistry<Object> registry = new AttachableRuntimeRegistry<>();
        var key = new AttachableRuntimeRegistry.RuntimeKey(UUID.randomUUID(),
                AttachableQueryContext.LogicalHand.OFF_HAND);
        Object first = registry.getOrCreate(key,
                new AttachableRuntimeRegistry.RuntimeIdentity("test:a", "test:a", 1L), 10L, Object::new);
        Object replaced = registry.getOrCreate(key,
                new AttachableRuntimeRegistry.RuntimeIdentity("test:b", "test:b", 2L), 11L, Object::new);

        assertNotSame(first, replaced);
        registry.evictOlderThan(12L);
        assertEquals(0, registry.size());
    }

    private record ObjectWithAmmo(int instance, int ammo) {
    }
}
