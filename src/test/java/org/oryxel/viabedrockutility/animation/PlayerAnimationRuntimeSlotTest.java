package org.oryxel.viabedrockutility.animation;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class PlayerAnimationRuntimeSlotTest {
    @Test
    void preservesRuntimeForSamePlayerAndLevel() {
        final Object player = new Object();
        final Object level = new Object();
        final AtomicInteger creations = new AtomicInteger();
        final Supplier<Object> factory = () -> {
            creations.incrementAndGet();
            return new Object();
        };
        final PlayerAnimationRuntimeSlot<Object> slot = new PlayerAnimationRuntimeSlot<>();

        final Object first = slot.bind(new PlayerAnimationOwner(player, level), factory);
        final Object second = slot.bind(new PlayerAnimationOwner(player, level), factory);

        assertSame(first, second);
        assertEquals(1, creations.get());
    }

    @Test
    void rebuildsForNewPlayerInstanceOrLevel() {
        final Object player = new Object();
        final Object level = new Object();
        final AtomicInteger creations = new AtomicInteger();
        final Supplier<Object> factory = () -> {
            creations.incrementAndGet();
            return new Object();
        };
        final PlayerAnimationRuntimeSlot<Object> slot = new PlayerAnimationRuntimeSlot<>();

        final Object first = slot.bind(new PlayerAnimationOwner(player, level), factory);
        final Object afterRespawn = slot.bind(new PlayerAnimationOwner(new Object(), level), factory);
        final Object afterDimensionChange = slot.bind(
                new PlayerAnimationOwner(new Object(), new Object()), factory);

        assertNotSame(first, afterRespawn);
        assertNotSame(afterRespawn, afterDimensionChange);
        assertEquals(3, creations.get());
    }

    @Test
    void explicitReplacementStartsAResourceReloadGeneration() {
        final Object player = new Object();
        final Object level = new Object();
        final PlayerAnimationRuntimeSlot<Object> slot = new PlayerAnimationRuntimeSlot<>();
        final Object beforeReload = new Object();
        final Object afterReload = new Object();

        slot.replace(beforeReload);
        assertSame(beforeReload, slot.bind(new PlayerAnimationOwner(player, level), Object::new));
        slot.replace(afterReload);

        assertSame(afterReload, slot.bind(new PlayerAnimationOwner(player, level), Object::new));
        assertNotSame(beforeReload, slot.current());
    }
}
