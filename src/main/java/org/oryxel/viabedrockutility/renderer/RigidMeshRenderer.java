package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.oryxel.viabedrockutility.attachable.AttachableSurfaces;
import org.oryxel.viabedrockutility.mixin.impl.accessor.PoseStackPoseAccessor;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Optional native geometry submission. Sampling, visibility and locator ownership remain upstream. */
public final class RigidMeshRenderer implements AutoCloseable {
    private static final Vector4f WHITE = new Vector4f(1);
    private static long draws, fallbackDraws;
    private final RigidMeshModel model;
    private final ArrayList<Draw> pool = new ArrayList<>(), visible = new ArrayList<>(), gpu = new ArrayList<>();
    private final Matrix4f normalUv = new Matrix4f();
    private final Vector3f lightData = new Vector3f();
    private final Vector4f colorData = new Vector4f();
    private boolean broken, closed;
    public RigidMeshRenderer(Model source) { model = new RigidMeshModel(source); }

    public static void prepareFrame() { RigidMeshMaterials.refresh(); RigidMeshStorage.prepareFrame(); }
    public static void clear(String reason) { RigidMeshStorage.clear(reason); RigidMeshMaterials.clear(); }
    /** Explicit read-only maintenance counters; no frame history, file writes or control switches. */
    public static Map<String, Long> statistics() {
        return Map.of("draws", draws, "fallback_draws", fallbackDraws,
                "meshes", (long)RigidMeshStorage.size(), "pending", (long)RigidMeshStorage.pending(),
                "uploads", RigidMeshStorage.uploads());
    }

    public boolean render(List<ModelPart> roots, PoseStack poses, VertexConsumer original,
                          RenderType type, boolean glint, int light, int overlay, int color, AttachableSurfaces.Uv uv) {
        if (closed || broken) return false;
        var pipeline = RigidMeshMaterials.pipeline(type, glint);
        if (pipeline == null) return false;
        visible.clear(); gpu.clear();
        try {
            model.prepare();
            if (roots == null) collect(model.root(), poses); else for (ModelPart part : roots) {
                var root = model.part(part);
                if (root == null) throw new IllegalArgumentException("Unknown rigid root");
                collect(root, poses);
            }
            GpuBufferSlice global = null;
            for (Draw command : visible) {
                if (!command.part.ready()) continue;
                if (global == null) global = RenderSystem.getDynamicUniforms().writeTransform(
                        RenderSystem.getModelViewMatrix(), WHITE, RenderSystem.getModelOffset(),
                        RenderSystem.getTextureMatrix(), RenderSystem.getShaderLineWidth());
                command.entry = command.part.geometry.entry;
                var pose = command.pose.last();
                normalUv.identity().set3x3(pose.normal());
                normalUv.m03(uv.scaleU()).m13(uv.scaleV()).m23(uv.offsetU()).m30(uv.offsetV());
                lightData.set(light & 65535, (light >>> 16) & 65535,
                        ((PoseStackPoseAccessor)(Object)pose).viaBedrockUtility$trustedNormals() ? 1 : 0);
                colorData.set(((color >>> 16) & 255) / 255f, ((color >>> 8) & 255) / 255f,
                        (color & 255) / 255f, ((color >>> 24) & 255) / 255f);
                command.global = global;
                command.rigid = RenderSystem.getDynamicUniforms().writeTransform(pose.pose(), colorData, lightData, normalUv, 1);
                gpu.add(command);
            }
        } catch (RuntimeException error) {
            broken = true;
            ViaBedrockUtilityNeoForge.LOGGER.warn("[RigidMesh] Invalid model preparation; retaining native rendering", error);
            visible.clear(); gpu.clear(); return false; // No vertices or draw calls committed yet.
        }
        try {
            MinecraftMeshDraw.draw(type, pipeline, gpu);
        } catch (RuntimeException error) {
            RigidMeshMaterials.failed(type);
            ViaBedrockUtilityNeoForge.LOGGER.warn("[RigidMesh] GPU submission failed; retaining unsubmitted parts on the native path", error);
        }
        try {
            // Never repeat a successful GPU draw. The original consumer still owns lighting, UV,
            // buffering and material state for pending uploads, unsupported geometry and failures.
            for (Draw command : visible) if (!command.done) {
                command.part.renderGeometry(command.pose, original, light, overlay, color); fallbackDraws++;
            }
            return true;
        } finally { visible.clear(); gpu.clear(); }
    }
    private void collect(RigidMeshModel.Part part, PoseStack poses) {
        if (!part.model.visible) return;
        poses.pushPose();
        try {
            part.model.translateAndRotate(poses);
            if (!part.model.skipDraw && !part.observed.isEmpty()) {
                int index = visible.size();
                if (index >= 4096) throw new IllegalArgumentException("Rigid draw budget exceeded");
                if (index == pool.size()) pool.add(new Draw());
                Draw command = pool.get(index); command.part = part; command.done = false;
                command.entry = null; command.global = null; command.rigid = null;
                command.pose.last().pose().set(poses.last().pose());
                command.pose.last().normal().set(poses.last().normal());
                ((PoseStackPoseAccessor)(Object)command.pose.last()).viaBedrockUtility$setTrustedNormals(
                        ((PoseStackPoseAccessor)(Object)poses.last()).viaBedrockUtility$trustedNormals());
                visible.add(command);
            }
            for (var child : part.children) collect(child, poses);
        } finally { poses.popPose(); }
    }
    @Override public void close() {
        if (closed) return; closed = true; model.close(); pool.clear(); visible.clear(); gpu.clear();
    }
    private static final class Draw implements MinecraftMeshDraw.Command {
        final PoseStack pose = new PoseStack();
        RigidMeshModel.Part part;
        GpuMeshEntry entry;
        GpuBufferSlice global, rigid;
        boolean done;
        public GpuMeshEntry mesh() { return entry; }
        public GpuBufferSlice transform() { return global; }
        public GpuBufferSlice rigidTransform() { return rigid; }
        public void drawn() { done = true; draws++; }
    }
}
