package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/** Shared immutable geometry only. Actor poses and native models never enter this index. */
final class RigidMeshStorage {
    private static final int MAX_PENDING = 4096;
    private static final int MAX_UPLOADS_PER_FRAME = 8;
    private static final long UPLOAD_BUDGET_NS = 1_500_000;
    private static final Map<String, Shared> GEOMETRY = new HashMap<>();
    private static final ArrayDeque<Runnable> PENDING = new ArrayDeque<>();
    private static final MessageDigest DIGEST = digest();
    private static long epoch, uploads;
    private RigidMeshStorage() {}
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new ExceptionInInitializerError(e); }
    }
    static long epoch() { return epoch; }
    static int size() { return GEOMETRY.size(); }
    static int pending() { return PENDING.size(); }
    static long uploads() { return uploads; }
    static boolean enqueue(Runnable upload) {
        if (PENDING.size() >= MAX_PENDING) return false;
        PENDING.addLast(upload); return true;
    }
    static void cancel(Runnable upload) { PENDING.remove(upload); }
    static void prepareFrame() {
        long deadline = System.nanoTime() + UPLOAD_BUDGET_NS;
        int count = 0;
        while (!PENDING.isEmpty() && count++ < MAX_UPLOADS_PER_FRAME) {
            PENDING.removeFirst().run();
            if (System.nanoTime() >= deadline) break;
        }
    }
    static Shared acquire(ByteBuffer vertices, int count) {
        DIGEST.reset(); DIGEST.update(vertices.duplicate());
        String key = HexFormat.of().formatHex(DIGEST.digest());
        Shared shared = GEOMETRY.get(key);
        if (shared != null && shared.entry.isValid()) { shared.references++; return shared; }
        if (shared != null) GEOMETRY.remove(key, shared);
        var handle = FrozenMeshDrawQueue.upload("rigid attachable", vertices);
        var entry = new GpuMeshEntry(handle, count, count / 4 * 6, VertexFormat.Mode.QUADS);
        if (!FrozenEntityMeshCache.global().add(entry)) return null;
        shared = new Shared(key, entry); GEOMETRY.put(key, shared); uploads++;
        VbuRenderMetrics.recordFrozenUpload(entry.sizeBytes());
        return shared;
    }
    static void clear(String reason) {
        epoch++; PENDING.clear();
        for (Shared shared : GEOMETRY.values()) FrozenEntityMeshCache.global().remove(shared.entry, reason);
        GEOMETRY.clear();
    }
    static final class Shared implements AutoCloseable {
        final String key; final GpuMeshEntry entry; int references = 1;
        Shared(String key, GpuMeshEntry entry) { this.key = key; this.entry = entry; }
        boolean current() { return entry.isValid() && FrozenEntityMeshCache.global().touch(entry); }
        @Override public void close() {
            if (--references == 0) {
                GEOMETRY.remove(key, this);
                FrozenEntityMeshCache.global().remove(entry, "rigid_owner_removed");
            }
        }
    }
}
