package org.oryxel.viabedrockutility.pack;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Keeps a resource generation open until its reload future and tracked tasks have finished. */
public final class ResourceReloadCloseFence {
    private final Map<Object, Generation> generations = new IdentityHashMap<>();

    public Generation begin(Object identity, Runnable closeAction) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(closeAction, "closeAction");

        synchronized (this.generations) {
            if (this.generations.containsKey(identity)) {
                throw new IllegalStateException("Resource generation is already tracked");
            }
            Generation generation = new Generation(identity, closeAction);
            this.generations.put(identity, generation);
            return generation;
        }
    }

    public void retire(Object identity, Runnable immediateCloseAction) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(immediateCloseAction, "immediateCloseAction");

        Generation generation;
        synchronized (this.generations) {
            generation = this.generations.get(identity);
            if (generation != null) {
                generation.retired = true;
            }
        }

        if (generation == null) {
            immediateCloseAction.run();
        } else {
            this.closeIfReady(generation);
        }
    }

    private void closeIfReady(Generation generation) {
        Runnable closeAction = null;
        synchronized (this.generations) {
            if (generation.retired
                    && generation.sealed
                    && generation.lifetimeDone
                    && generation.activeTasks == 0
                    && this.generations.remove(generation.identity, generation)) {
                generation.closed = true;
                closeAction = generation.closeAction;
            }
        }

        if (closeAction != null) {
            closeAction.run();
        }
    }

    public final class Generation {
        private final Object identity;
        private final Runnable closeAction;
        private int activeTasks = 1;
        private boolean sealed;
        private boolean lifetimeDone;
        private boolean retired;
        private boolean closed;

        private Generation(Object identity, Runnable closeAction) {
            this.identity = identity;
            this.closeAction = closeAction;
        }

        public Executor tracking(Executor delegate) {
            Objects.requireNonNull(delegate, "delegate");
            return command -> {
                this.reserveTask();
                try {
                    delegate.execute(() -> {
                        try {
                            command.run();
                        } finally {
                            this.finishTask();
                        }
                    });
                } catch (RuntimeException | Error failure) {
                    this.finishTask();
                    throw failure;
                }
            };
        }

        public void seal(CompletableFuture<?> lifetime) {
            Objects.requireNonNull(lifetime, "lifetime");
            synchronized (ResourceReloadCloseFence.this.generations) {
                if (this.sealed) {
                    throw new IllegalStateException("Resource generation is already sealed");
                }
                this.sealed = true;
            }

            lifetime.whenComplete((ignored, failure) -> {
                synchronized (ResourceReloadCloseFence.this.generations) {
                    this.lifetimeDone = true;
                }
                ResourceReloadCloseFence.this.closeIfReady(this);
            });
            this.finishTask();
        }

        private void reserveTask() {
            synchronized (ResourceReloadCloseFence.this.generations) {
                if (this.closed) {
                    throw new IllegalStateException("Resource generation is already closed");
                }
                this.activeTasks++;
            }
        }

        private void finishTask() {
            synchronized (ResourceReloadCloseFence.this.generations) {
                if (this.activeTasks <= 0) {
                    throw new IllegalStateException("Resource generation task count underflow");
                }
                this.activeTasks--;
            }
            ResourceReloadCloseFence.this.closeIfReady(this);
        }
    }
}
