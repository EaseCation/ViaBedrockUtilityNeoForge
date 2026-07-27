package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.oryxel.viabedrockutility.mixin.impl.accessor.CompositeRenderTypeAccessor;
import org.oryxel.viabedrockutility.mixin.impl.accessor.CompositeStateAccessor;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

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
        if (!(renderType instanceof CompositeRenderTypeAccessor renderTypeAccessor)) {
            throw new IllegalStateException("Unsupported non-composite RenderType: " + renderType);
        }

        RenderPipeline pipeline = renderTypeAccessor.viaBedrockUtility$getRenderPipeline();
        RenderType.CompositeState state = renderTypeAccessor.viaBedrockUtility$getCompositeState();
        RenderStateShard.OutputStateShard output =
                ((CompositeStateAccessor) (Object) state).viaBedrockUtility$getOutputState();
        RenderTarget target = output.getRenderTarget();
        GpuTextureView colorTarget = RenderSystem.outputColorTextureOverride != null
                ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
        GpuTextureView depthTarget = target.useDepth
                ? (RenderSystem.outputDepthTextureOverride != null
                    ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView())
                : null;

        int maxIndexCount = 0;
        for (FrozenMeshDrawQueue.DrawRecord record : records) {
            maxIndexCount = Math.max(maxIndexCount, record.entry().indexCount());
        }
        RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(records.get(0).entry().mode());
        GpuBuffer indexBuffer = sequential.getBuffer(maxIndexCount);
        VertexFormat.IndexType indexType = sequential.type();
        GpuBufferSlice[] transforms = new GpuBufferSlice[records.size()];
        for (int i = 0; i < records.size(); i++) {
            transformedPose.set(RenderSystem.getModelViewMatrix()).mul(records.get(i).rootPose());
            transforms[i] = RenderSystem.getDynamicUniforms().writeTransform(
                    transformedPose, WHITE, RenderSystem.getModelOffset(),
                    RenderSystem.getTextureMatrix(), RenderSystem.getShaderLineWidth());
        }

        renderType.setupRenderState();
        try {
            GpuDevice device = RenderSystem.getDevice();
            CommandEncoder encoder = device.createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(
                    () -> "VBU frozen entities: " + renderType.getName(),
                    colorTarget, OptionalInt.empty(), depthTarget, OptionalDouble.empty())) {
                pass.setPipeline(pipeline);
                ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
                if (scissor.enabled()) {
                    pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
                }
                RenderSystem.bindDefaultUniforms(pass);
                for (int unit = 0; unit < 12; unit++) {
                    GpuTextureView texture = RenderSystem.getShaderTexture(unit);
                    if (texture != null) {
                        pass.bindSampler("Sampler" + unit, texture);
                    }
                }
                pass.setIndexBuffer(indexBuffer, indexType);

                for (int i = 0; i < records.size(); i++) {
                    FrozenMeshDrawQueue.DrawRecord record = records.get(i);
                    FrozenMeshEntry entry = record.entry();
                    if (!(entry.handle() instanceof GpuHandle handle) || !entry.isValid()) {
                        throw new IllegalStateException("Frozen mesh buffer was released before draw");
                    }
                    pass.setUniform("DynamicTransforms", transforms[i]);
                    pass.setVertexBuffer(0, handle.buffer);
                    pass.drawIndexed(0, 0, entry.indexCount(), 1);
                    record.markDrawn();
                    VbuRenderMetrics.recordFrozenDraw(entry.vertexCount());
                }
            }
        } finally {
            renderType.clearRenderState();
        }
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
