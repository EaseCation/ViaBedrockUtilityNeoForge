package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.EntityVertex;
import org.lwjgl.system.MemoryStack;

/**
 * Holder-class that isolates every reference to Sodium's {@link VertexBufferWriter} / {@link EntityVertex}
 * so that when Sodium is absent at runtime this class's static initializer still succeeds
 * ({@link #SUPPORTED} = false) and the JIT folds away the C2 push branch in {@code CuboidMixin} — zero
 * Sodium footprint, no {@code NoClassDefFoundError}.
 *
 * <p>All Sodium symbol references live in method bodies here, which the JVM links lazily (only when
 * actually called). {@code CuboidMixin} passes the writer around as an opaque {@code Object} and goes
 * through these static methods, so it never directly references a Sodium type.
 */
public final class SodiumPushBackend {
    public static final boolean SUPPORTED;
    static {
        boolean ok;
        try {
            Class.forName("net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter");
            ok = true;
        } catch (ClassNotFoundException e) {
            ok = false;
        }
        SUPPORTED = ok;
    }

    private SodiumPushBackend() {}

    /**
     * Returns {@code buffer} as a {@link VertexBufferWriter} (opaque {@code Object} to keep the caller
     * Sodium-agnostic), or {@code null} if the buffer does not implement VertexBufferWriter (no Sodium,
     * or a non-Sodium consumer such as an outline/decal wrapper).
     */
    public static Object tryOf(VertexConsumer buffer) {
        return VertexBufferWriter.tryOf(buffer);
    }

    /** EntityVertex.FORMAT (NEW_ENTITY) vertex stride in bytes (= 36). */
    public static int stride() {
        return EntityVertex.STRIDE;
    }

    /**
     * Writes one entity vertex (36 bytes) at {@code ptr}: position, color (ABGR int), uv0 (texture),
     * overlay, light, and packed (NormI8) normal. Layout matches {@code DefaultVertexFormat.NEW_ENTITY}.
     */
    public static void writeVertex(long ptr, float x, float y, float z, int color,
                                   float u, float v, int overlay, int light, int packedNormal) {
        EntityVertex.write(ptr, x, y, z, color, u, v, overlay, light, packedNormal);
    }

    /**
     * Pushes {@code count} pre-assembled vertices (each {@link #stride()} bytes, starting at {@code ptr})
     * into the buffer behind {@code writer}. Sodium implements this as a single bulk {@code copyMemory},
     * bypassing the per-vertex {@code addVertex} interface dispatch that dominates VBU compile CPU.
     */
    public static void push(MemoryStack stack, Object writer, long ptr, int count) {
        ((VertexBufferWriter) writer).push(stack, ptr, count, EntityVertex.FORMAT);
    }
}
