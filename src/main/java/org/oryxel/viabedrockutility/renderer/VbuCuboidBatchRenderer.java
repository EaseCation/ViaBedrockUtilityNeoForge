package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.ARGB;
import org.oryxel.viabedrockutility.mixin.interfaces.ICuboid;

/** Sodium-free entry point for emitting all VBU cuboids of one model part with a single push. */
public final class VbuCuboidBatchRenderer {
    private VbuCuboidBatchRenderer() {
    }

    public static Batch compile(ModelPart.Cube[] cuboids) {
        if (cuboids.length == 0) {
            return null;
        }

        final ICuboid[] states = new ICuboid[cuboids.length];
        final VbuCompiledCuboid[] geometries = new VbuCompiledCuboid[cuboids.length];
        int totalVertexCount = 0;
        for (int i = 0; i < cuboids.length; i++) {
            final ICuboid state = (ICuboid) (Object) cuboids[i];
            final VbuCompiledCuboid geometry = state.viaBedrockUtility$getCompiledGeometry();
            if (!state.viaBedrockUtility$isVBUCuboid() || geometry == null) {
                return null;
            }
            states[i] = state;
            geometries[i] = geometry;
            totalVertexCount = Math.addExact(totalVertexCount, geometry.vertexCount());
        }
        return new Batch(states, geometries, totalVertexCount);
    }

    /** Returns false without writing anything when this consumer cannot use the optional bulk writer. */
    public static boolean tryRender(PoseStack.Pose pose,
                                    VertexConsumer consumer,
                                    Batch batch,
                                    int light,
                                    int overlay,
                                    int color,
                                    boolean flatNormal) {
        final Object writer = VbuCompileScratch.tryWriter(consumer);
        if (writer == null) {
            return false;
        }
        if (batch.totalVertexCount == 0) {
            VbuRenderMetrics.recordEmptyBatch(batch);
            return true;
        }
        if (!VbuCompileScratch.tryBeginPush()) {
            return false;
        }

        try {
            final int stride = SodiumPushBackend.stride();
            final long buffer = VbuCompileScratch.acquirePushBuffer(batch.totalVertexCount, stride);
            final int colorAbgr = ARGB.toABGR(color);
            long pointer = buffer;
            int emittedVertexCount = 0;

            for (int i = 0; i < batch.geometries.length; i++) {
                final int emitted = batch.geometries[i].writeVertices(
                        pose,
                        pointer,
                        stride,
                        colorAbgr,
                        overlay,
                        light,
                        batch.states[i].viaBedrockUtility$getVOffset(),
                        flatNormal);
                pointer += (long) emitted * stride;
                emittedVertexCount += emitted;
            }

            SodiumPushBackend.push(writer, buffer, emittedVertexCount);
            VbuRenderMetrics.recordBatch(batch);
            return true;
        } finally {
            VbuCompileScratch.endPush();
        }
    }

    public static final class Batch {
        private final ICuboid[] states;
        private final VbuCompiledCuboid[] geometries;
        private final int totalVertexCount;
        private final int boxVertexCount;

        Batch(ICuboid[] states, VbuCompiledCuboid[] geometries, int totalVertexCount) {
            if (states.length != geometries.length || totalVertexCount < 0) {
                throw new IllegalArgumentException("Invalid compiled cuboid batch");
            }
            this.states = states;
            this.geometries = geometries;
            this.totalVertexCount = totalVertexCount;
            int boxVertices = 0;
            for (VbuCompiledCuboid geometry : geometries) {
                if (geometry.isBox()) {
                    boxVertices = Math.addExact(boxVertices, geometry.vertexCount());
                }
            }
            this.boxVertexCount = boxVertices;
        }

        int cuboidCount() {
            return this.geometries.length;
        }

        int totalVertexCount() {
            return this.totalVertexCount;
        }

        int boxVertexCount() {
            return this.boxVertexCount;
        }

        int genericVertexCount() {
            return this.totalVertexCount - this.boxVertexCount;
        }
    }
}
