package org.oryxel.viabedrockutility.renderer;

import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

/**
 * Render-thread scratch buffers for VBU's fast compile path
 * (see {@link org.oryxel.viabedrockutility.mixin.impl.render.CuboidMixin}).
 *
 * <p>Minecraft's entity render is single-threaded (the render thread calls {@code ModelPart.Cube.compile}
 * serially per cube), so static scratch is reused across all cubes — zero per-cube allocation.
 */
public final class VbuCompileScratch {
    /** Up to 8 transformed world-space corner positions, rewritten by the fast compile path each cube. */
    public static final Vector3f[] CORNER_WORLD = new Vector3f[8];

    // C2.1: off-heap scratch buffer for the push fast path — allocated ONCE and reused per cube
    // (render-thread only, serial compile). Replaces MemoryStack.stackPush() per cube: that ThreadLocal
    // lookup was +4.8% in JFR. Sodium's BufferBuilder.push does NOT use its MemoryStack arg
    // (javap-confirmed: only reserve + copyMemory), so CuboidMixin writes vertices here and pushes with
    // a null stack. 24 vertices * 36 bytes (NEW_ENTITY stride) = 864 bytes.
    // Backing ByteBuffer kept as a strong reference so its off-heap memory isn't freed by GC;
    // PUSH_BUFFER is the raw address passed to EntityVertex.write / push.
    private static final java.nio.ByteBuffer PUSH_BUFFER_OBJ = MemoryUtil.memAlloc(24 * 36);
    public static final long PUSH_BUFFER = MemoryUtil.memAddress(PUSH_BUFFER_OBJ);

    // Plan C (emissive): set by CustomEntityRenderer.render before renderToBuffer when the model uses
    // ignore_lighting / USE_EMISSIVE, read by CuboidMixin to force normals to (0,1,0) for flat-bright
    // shading. Replaces the old FlatNormalVertexConsumer wrapper (which did not implement VertexBufferWriter
    // and would have disabled the C2 push path). Render-thread only — no synchronization needed.
    // CustomEntityRenderer resets it to false in a finally block after renderToBuffer returns.
    public static boolean FLAT_NORMAL = false;

    static {
        for (int i = 0; i < CORNER_WORLD.length; i++) {
            CORNER_WORLD[i] = new Vector3f();
        }
    }

    private VbuCompileScratch() {}
}
