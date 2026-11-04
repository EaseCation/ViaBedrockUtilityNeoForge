package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.RenderType;
import org.oryxel.viabedrockutility.mixin.impl.accessor.CompositeRenderTypeAccessor;
import org.oryxel.viabedrockutility.mixin.impl.accessor.CompositeStateAccessor;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/** One native submission path for pose-frozen entities and local rigid geometry. */
final class MinecraftMeshDraw {
    interface Command {
        GpuMeshEntry mesh();
        GpuBufferSlice transform();
        default GpuBufferSlice rigidTransform() { return null; }
        void drawn();
    }
    private MinecraftMeshDraw() {}
    static void draw(RenderType type, RenderPipeline pipeline, List<? extends Command> commands) {
        if (commands.isEmpty()) return;
        RenderSystem.assertOnRenderThread();
        var accessor = (CompositeRenderTypeAccessor) type;
        var state = (CompositeStateAccessor)(Object)accessor.viaBedrockUtility$getCompositeState();
        var target = state.viaBedrockUtility$getOutputState().getRenderTarget();
        var color = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
        var depth = target.useDepth ? (RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView()) : null;
        int maximum = 0;
        for (Command command : commands) {
            MinecraftFrozenMeshBackend.vertexBuffer(command.mesh()); // Validate every handle before committing any draw.
            maximum = Math.max(maximum, command.mesh().indexCount());
        }
        var sequential = RenderSystem.getSequentialBuffer(commands.getFirst().mesh().mode());
        var indices = sequential.getBuffer(maximum);
        type.setupRenderState();
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "VBU cached geometry: " + type.getName(), color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            var scissor = RenderSystem.getScissorStateForRenderTypeDraws();
            if (scissor.enabled()) pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
            RenderSystem.bindDefaultUniforms(pass);
            for (int unit = 0; unit < 12; unit++) {
                var texture = RenderSystem.getShaderTexture(unit);
                if (texture != null) pass.bindSampler("Sampler" + unit, texture);
            }
            pass.setIndexBuffer(indices, sequential.type());
            for (Command command : commands) {
                pass.setUniform("DynamicTransforms", command.transform());
                if (command.rigidTransform() != null) pass.setUniform("RigidTransforms", command.rigidTransform());
                pass.setVertexBuffer(0, MinecraftFrozenMeshBackend.vertexBuffer(command.mesh()));
                pass.drawIndexed(0, 0, command.mesh().indexCount(), 1);
                command.drawn();
                VbuRenderMetrics.recordFrozenDraw(command.mesh().vertexCount());
            }
        } finally { type.clearRenderState(); }
    }
}
