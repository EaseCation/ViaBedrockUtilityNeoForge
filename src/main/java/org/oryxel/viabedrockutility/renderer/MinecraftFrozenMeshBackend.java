package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.oryxel.viabedrockutility.mixin.impl.accessor.CompositeRenderTypeAccessor;

import java.nio.ByteBuffer;
import java.util.List;

/** Minecraft 1.21.8 persistent GpuBuffer backend. */
public final class MinecraftFrozenMeshBackend implements FrozenMeshBackend {
    public static final MinecraftFrozenMeshBackend INSTANCE = new MinecraftFrozenMeshBackend();
    private static final Vector4f WHITE = new Vector4f(1.0F);

    private final Matrix4f transformedPose = new Matrix4f();

    private MinecraftFrozenMeshBackend() {
    }

    @Override
    public Handle upload(String name, ByteBuffer vertices) {
        RenderSystem.assertOnRenderThread();
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
                () -> "VBU frozen entity: " + name, GpuBuffer.USAGE_VERTEX, vertices);
        return new GpuHandle(buffer);
    }

    @Override
    public void drawGroup(RenderType renderType, List<FrozenMeshDrawQueue.DrawRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        if (!(renderType instanceof CompositeRenderTypeAccessor accessor)) {
            throw new IllegalStateException("Unsupported non-composite RenderType: " + renderType);
        }
        var commands = new java.util.ArrayList<MinecraftMeshDraw.Command>(records.size());
        for (var record : records) {
            transformedPose.set(RenderSystem.getModelViewMatrix()).mul(record.rootPose());
            var transform = RenderSystem.getDynamicUniforms().writeTransform(
                    transformedPose, WHITE, RenderSystem.getModelOffset(),
                    RenderSystem.getTextureMatrix(), RenderSystem.getShaderLineWidth());
            commands.add(new MinecraftMeshDraw.Command() {
                public GpuMeshEntry mesh() { return record.entry(); }
                public GpuBufferSlice transform() { return transform; }
                public void drawn() { record.markDrawn(); }
            });
        }
        MinecraftMeshDraw.draw(renderType, accessor.viaBedrockUtility$getRenderPipeline(), commands);
    }

    /** Native buffer access stays inside the Minecraft renderer; no reflection or backend casts in consumers. */
    public static GpuBuffer vertexBuffer(GpuMeshEntry entry) {
        if (!entry.isValid() || !(entry.handle() instanceof GpuHandle handle)) {
            throw new IllegalStateException("GPU mesh is unavailable for the Minecraft backend");
        }
        return handle.buffer;
    }

    private static final class GpuHandle implements Handle {
        private final GpuBuffer buffer;
        private final long size;
        private boolean closed;

        private GpuHandle(GpuBuffer buffer) {
            this.buffer = buffer;
            this.size = buffer.size();
        }

        @Override public long sizeBytes() { return size; }
        @Override public boolean isClosed() { return closed || buffer.isClosed(); }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (RenderSystem.isOnRenderThread()) {
                buffer.close();
            } else {
                Minecraft.getInstance().execute(() -> {
                    if (!buffer.isClosed()) {
                        buffer.close();
                    }
                });
            }
        }
    }
}
