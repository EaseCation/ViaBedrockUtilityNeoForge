package org.oryxel.viabedrockutility.animation;

import java.util.Objects;
import java.util.function.Supplier;

/** Keeps controller state only while the same player object remains in the same client level. */
public final class PlayerAnimationRuntimeSlot<T> {
    private T runtime;
    private PlayerAnimationOwner owner;

    public T bind(PlayerAnimationOwner nextOwner, Supplier<T> factory) {
        Objects.requireNonNull(nextOwner, "nextOwner");
        Objects.requireNonNull(factory, "factory");
        if (runtime == null || (owner != null && !owner.hasSameInstances(nextOwner))) {
            runtime = Objects.requireNonNull(factory.get(), "runtime");
        }
        owner = nextOwner;
        return runtime;
    }

    public void replace(T nextRuntime) {
        runtime = Objects.requireNonNull(nextRuntime, "nextRuntime");
        owner = null;
    }

    public T current() {
        return runtime;
    }
}
