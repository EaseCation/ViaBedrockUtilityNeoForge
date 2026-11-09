package org.oryxel.viabedrockutility.pack.processor;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Bounded native-image ownership across a worker and the render thread. No caller waits for decoding. */
final class DeferredTextureQueue<T extends AutoCloseable> {
    record Progress(long bytes, boolean complete) { }
    interface Upload<T> { Progress step(T value, long byteAllowance); }
    private final Executor executor;
    private final int maximumJobs;
    private final long maximumBytes;
    private final ArrayDeque<Ticket> ready = new ArrayDeque<>();
    private int jobs;
    private long reserved;

    DeferredTextureQueue(Executor executor, int maximumJobs, long maximumBytes) {
        this.executor = Objects.requireNonNull(executor);
        if (maximumJobs < 1 || maximumBytes < 1) throw new IllegalArgumentException("Texture budget");
        this.maximumJobs = maximumJobs;
        this.maximumBytes = maximumBytes;
    }

    synchronized Ticket request(long bytes, Supplier<T> decode, Upload<T> upload, Consumer<Throwable> failure) {
        if (bytes < 1 || bytes > maximumBytes || jobs >= maximumJobs || bytes > maximumBytes - reserved) return null;
        var ticket = new Ticket(bytes, decode, upload, failure);
        jobs++;
        reserved += bytes;
        try { executor.execute(ticket::decode); }
        catch (RejectedExecutionException rejected) { ticket.release(null); return null; }
        return ticket;
    }

    /** Called only by the render thread. A single driver call cannot be preempted by the time budget. */
    void pump(long byteBudget, int operationBudget, long nanos, LongSupplier clock) {
        long started = clock.getAsLong();
        for (int i = 0; i < operationBudget && byteBudget > 0 && clock.getAsLong() - started < nanos; i++) {
            Ticket ticket;
            synchronized (this) { ticket = ready.pollFirst(); }
            if (ticket == null) break;
            long consumed = ticket.upload(byteBudget);
            byteBudget -= consumed;
        }
    }

    synchronized long reservedBytes() { return reserved; }
    synchronized int pendingJobs() { return jobs; }

    final class Ticket implements AutoCloseable {
        private final long bytes;
        private Supplier<T> decoder;
        private final Upload<T> uploader;
        private final Consumer<Throwable> failure;
        private T value;
        private boolean decoding, cancelled, released, complete;
        private Ticket(long bytes, Supplier<T> decoder, Upload<T> uploader, Consumer<Throwable> failure) {
            this.bytes=bytes;this.decoder=decoder;this.uploader=uploader;this.failure=failure;
        }
        private void decode() {
            Supplier<T> work;
            synchronized (DeferredTextureQueue.this) {
                if (cancelled || released) { release(null); return; }
                decoding=true;work=decoder;decoder=null;
            }
            T decoded=null;Throwable error=null;
            try { decoded=Objects.requireNonNull(work.get()); }
            catch (Throwable problem) { error=problem; }
            synchronized (DeferredTextureQueue.this) {
                decoding=false;
                if (cancelled || error!=null) release(decoded);
                else { value=decoded;ready.addLast(this); }
            }
            if(error!=null&&!cancelled) failure.accept(error);
        }
        private long upload(long allowance) {
            T image;
            synchronized (DeferredTextureQueue.this) {
                if(cancelled||released)return 0;
                image=value;
            }
            try {
                Progress progress=uploader.step(image,allowance);
                if(progress.bytes()<0||progress.bytes()>allowance)throw new IllegalStateException("Texture upload exceeded byte budget");
                synchronized (DeferredTextureQueue.this) {
                    if(progress.complete()){complete=true;release(value);}
                    else if(!cancelled)ready.addLast(this);
                }
                return progress.bytes();
            } catch(Throwable error) {
                synchronized (DeferredTextureQueue.this){release(value);}
                failure.accept(error);
                return allowance;
            }
        }
        boolean complete() { synchronized (DeferredTextureQueue.this){return complete;} }
        private void release(T decoded) {
            if(decoded!=null)try{decoded.close();}catch(Exception ignored){}
            value=null;decoder=null;
            if(!released){released=true;jobs--;reserved-=bytes;}
        }
        /** Render-thread cancellation; a running decode retains its reservation until it really exits. */
        @Override public void close() {
            synchronized (DeferredTextureQueue.this) {
                cancelled=true;ready.remove(this);
                if(!decoding)release(value);
            }
        }
    }
}
