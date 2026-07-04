package org.oryxel.viabedrockutility.mixin.impl.render;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.mixin.interfaces.ICuboid;
import org.oryxel.viabedrockutility.renderer.SodiumPushBackend;
import org.oryxel.viabedrockutility.renderer.VbuCompileScratch;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.Cube.class)
public abstract class CuboidMixin implements ICuboid {
    @Shadow @Final public ModelPart.Polygon[] polygons;

    @Unique
    private boolean isVBU = false;

    @Unique
    private float vOffset = 0f;

    // Reused scratch vector for transformNormal output (single-threaded render path).
    @Unique
    private final Vector3f tempVec = new Vector3f();

    // ===== 8-corner cache (built once per cube, reused by the C2 push path) =====
    // A box cube's polygon vertices share up to 8 unique pos Vector3f objects (correctUv reuses 8
    // stack-local Vertex instances; remap shares pos). We bucket by pos identity (==) so the push path
    // transforms 8 corners once and looks up the result per vertex. cornerCount >= 0 => box (fast path);
    // -1 => poly_mesh / non-box (vertices not shared) => fallback.
    @Unique
    private final Vector3f[] vbu$cornerPos = new Vector3f[8];

    @Unique
    private final byte[] vbu$vertexCornerIndex = new byte[24];

    @Unique
    private int vbu$cornerCount = 0;

    @Unique
    private boolean vbu$cacheBuilt = false;

    // Packed NormI8 normal pointing straight up (0,1,0): byte0=0, byte1=127, byte2=0 => 0x00007F00.
    // Used for flat/emissive lighting (FLAT_NORMAL) so every face shades fully bright.
    @Unique
    private static final int VBU$PACKED_NORMAL_UP = 0x00007F00;

    @Override
    public boolean viaBedrockUtility$isVBUCuboid() {
        return this.isVBU;
    }

    @Override
    public void viaBedrockUtility$markAsVBU() {
        this.isVBU = true;
    }

    @Override
    public float viaBedrockUtility$getVOffset() {
        return this.vOffset;
    }

    @Override
    public void viaBedrockUtility$setVOffset(float offset) {
        this.vOffset = offset;
    }

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void vbu$renderCuboid(PoseStack.Pose entry, VertexConsumer buffer, int light, int overlay, int color, CallbackInfo ci) {
        // Always take over compile for VBU cuboids. We CANNOT defer to Sodium's CubeMixin.onCompile: VBU
        // cuboids use per-face Bedrock UVs (correctUv rewrites each polygon vertex's u/v after construction,
        // but Sodium's ModelCuboid bakes UVs from texOff=0 during <init> — before correctUv — so deferring
        // scrambles UVs). Instead we run our own compile below.
        if (!this.isVBU) return;
        ci.cancel();

        if (!this.vbu$cacheBuilt) {
            this.vbu$buildCornerCache();
        }
        final boolean flatNormal = VbuCompileScratch.FLAT_NORMAL;

        // C2 fast path: Sodium present + buffer is a VertexBufferWriter + box cube (8 corners) =>
        // assemble vertices in a MemoryStack and push once (copyMemory), bypassing 24 addVertex dispatches.
        if (this.vbu$cornerCount >= 0 && SodiumPushBackend.SUPPORTED) {
            final Object writer = SodiumPushBackend.tryOf(buffer);
            if (writer != null) {
                this.vbu$renderCuboidFastPush(entry, writer, light, overlay, color, flatNormal);
                return;
            }
        }

        // Fallback: per-vertex transform + addVertex (poly_mesh, Sodium absent, or non-VertexBufferWriter buffer).
        this.vbu$renderCuboidFallback(entry, buffer, light, overlay, color, flatNormal);
    }

    /**
     * C2 push fast path: transform the 8 unique corners once, write up to 24 vertices into a MemoryStack
     * via {@link SodiumPushBackend#writeVertex} (EntityVertex layout), then push once. The single push is
     * a bulk copyMemory into the buffer — it replaces 24 {@code addVertex} interface dispatches (the
     * actual compile bottleneck). UV is read straight from each polygon vertex, so per-face Bedrock UVs
     * are preserved exactly.
     */
    @Unique
    private void vbu$renderCuboidFastPush(PoseStack.Pose entry, Object writer, int light, int overlay, int color, boolean flatNormal) {
        // 1. Transform the 8 unique corners (reused across all faces of this cube).
        final Matrix4f posMatrix = entry.pose();
        final Vector3f[] world = VbuCompileScratch.CORNER_WORLD;
        final Vector3f[] cornerPos = this.vbu$cornerPos;
        final int cornerCount = this.vbu$cornerCount;
        for (int c = 0; c < cornerCount; c++) {
            Vector3f p = cornerPos[c];
            posMatrix.transformPosition(p.x() / 16.0f, p.y() / 16.0f, p.z() / 16.0f, world[c]);
        }

        final int stride = SodiumPushBackend.stride();
        final byte[] index = this.vbu$vertexCornerIndex;
        final float vOffset = this.vOffset;
        // C2.1: write into a static off-heap scratch buffer (allocated once in VbuCompileScratch) instead
        // of MemoryStack.stackPush() per cube. stackPush's ThreadLocal lookup was +4.8% in JFR; Sodium's
        // BufferBuilder.push does NOT use its MemoryStack arg (javap-confirmed: only reserve + copyMemory),
        // so we push with a null stack. Render-thread only + serial compile => no synchronization needed.
        final long base = VbuCompileScratch.PUSH_BUFFER;
        long ptr = base;
        int vi = 0;
        for (ModelPart.Polygon quad : this.polygons) {
            if (quad == null) continue;

            final int packedNormal;
            if (flatNormal) {
                packedNormal = VBU$PACKED_NORMAL_UP;
            } else {
                Vector3f tn = entry.transformNormal(quad.normal(), this.tempVec);
                packedNormal = vbu$packNormal(tn.x(), tn.y(), tn.z());
            }

            for (ModelPart.Vertex vertex : quad.vertices()) {
                Vector3f w = world[index[vi++]];
                SodiumPushBackend.writeVertex(ptr, w.x(), w.y(), w.z(), color,
                        vertex.u(), vertex.v() + vOffset, overlay, light, packedNormal);
                ptr += stride;
            }
        }
        // Single bulk copy of vi vertices into the buffer — replaces vi * addVertex dispatches.
        // stack=null: BufferBuilder.push ignores it (reserve + copyMemory only).
        SodiumPushBackend.push(null, writer, base, vi);
    }

    /**
     * Fallback: per-vertex transform + {@code addVertex} (vanilla compile semantics). Used for poly_mesh
     * (cornerCount < 0), when Sodium is absent (SUPPORTED false), or when the buffer is not a
     * VertexBufferWriter. Behavior matches the pre-C2 compile, with FLAT_NORMAL forcing normals up for
     * emissive models so the fallback path stays visually correct without FlatNormalVertexConsumer.
     */
    @Unique
    private void vbu$renderCuboidFallback(PoseStack.Pose entry, VertexConsumer buffer, int light, int overlay, int color, boolean flatNormal) {
        final Matrix4f posMatrix = entry.pose();
        final float vOffset = this.vOffset;
        for (ModelPart.Polygon quad : this.polygons) {
            if (quad == null) continue;

            final float nx, ny, nz;
            if (flatNormal) {
                nx = 0f;
                ny = 1f;
                nz = 0f;
            } else {
                Vector3f tn = entry.transformNormal(quad.normal(), this.tempVec);
                nx = tn.x();
                ny = tn.y();
                nz = tn.z();
            }

            for (ModelPart.Vertex vertex : quad.vertices()) {
                float wx = vertex.pos().x() / 16.0f;
                float wy = vertex.pos().y() / 16.0f;
                float wz = vertex.pos().z() / 16.0f;
                Vector3f pos = posMatrix.transformPosition(wx, wy, wz, this.tempVec);
                buffer.addVertex(pos.x(), pos.y(), pos.z(), color, vertex.u(), vertex.v() + vOffset, overlay, light, nx, ny, nz);
            }
        }
    }

    /**
     * Groups the cube's polygon vertices into unique corners by pos identity (==). Box cubes share <=8
     * pos objects; poly_mesh allocates each independently (>8). Traversal order MUST match the fast-path
     * emit loop so {@code vbu$vertexCornerIndex[vi]} aligns with the vertex visited at step {@code vi}.
     */
    @Unique
    private void vbu$buildCornerCache() {
        final Vector3f[] cornerPos = this.vbu$cornerPos;
        final byte[] index = this.vbu$vertexCornerIndex;
        int cornerCount = 0;
        int vi = 0;
        boolean valid = true;

        for (ModelPart.Polygon quad : this.polygons) {
            if (quad == null) continue;
            for (ModelPart.Vertex vertex : quad.vertices()) {
                if (vi >= 24) { valid = false; break; }
                Vector3f pos = vertex.pos();
                int bucket = -1;
                for (int c = 0; c < cornerCount; c++) {
                    if (cornerPos[c] == pos) { bucket = c; break; }
                }
                if (bucket == -1) {
                    if (cornerCount >= 8) { valid = false; break; }
                    cornerPos[cornerCount] = pos;
                    bucket = cornerCount++;
                }
                index[vi++] = (byte) bucket;
            }
            if (!valid) break;
        }

        this.vbu$cornerCount = valid ? cornerCount : -1;
        this.vbu$cacheBuilt = true;
    }

    /** Pack a normal (x,y,z in [-1,1]) into the int format MC's NEW_ENTITY normal slot uses: 3 signed bytes, little-endian. */
    @Unique
    private static int vbu$packNormal(float x, float y, float z) {
        return vbu$normalByte(x)
             | (vbu$normalByte(y) << 8)
             | (vbu$normalByte(z) << 16);
    }

    @Unique
    private static int vbu$normalByte(float v) {
        return (byte) (Mth.clamp(v, -1.0F, 1.0F) * 127.0F) & 0xFF;
    }
}
