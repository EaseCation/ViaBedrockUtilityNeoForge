package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import org.oryxel.viabedrockutility.mixin.interfaces.ICuboid;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.*;

/** Geometry preparation belongs to the native model lifetime, never to an animation frame. */
public final class RigidMeshModel implements AutoCloseable {
    private static final int MAX_PARTS = 4096;
    private static final int MAX_VERTICES_PER_PART = 262144;
    private final Model model;
    private final IdentityHashMap<ModelPart, Part> parts = new IdentityHashMap<>();
    private final List<IModelPart> watched = new ArrayList<>();
    private final Runnable topologyChanged = this::invalidate;
    private boolean dirty = true, closed;
    private Part root;

    public RigidMeshModel(Model model) { this.model = Objects.requireNonNull(model); prepare(); }
    static IModelPart extension(ModelPart part) { return (IModelPart)(Object)part; }
    private void invalidate() { dirty = true; }
    public void prepare() {
        if (closed) throw new IllegalStateException("Retired rigid mesh model");
        if (!dirty) return;
        releaseParts();
        try { root = build(model.root(), Collections.newSetFromMap(new IdentityHashMap<>())); dirty = false; }
        catch (RuntimeException error) { releaseParts(); throw error; }
    }
    private Part build(ModelPart part, Set<ModelPart> ancestors) {
        if (!ancestors.add(part) || parts.size() >= MAX_PARTS) throw new IllegalArgumentException("Invalid rigid mesh topology");
        IModelPart extension = extension(part);
        if (!extension.viaBedrockUtility$isVBUModel()) throw new IllegalArgumentException("Not a VBU mesh");
        watch(extension);
        List<ModelPart> groups = new ArrayList<>(); List<Part> children = new ArrayList<>();
        for (ModelPart child : extension.viaBedrockUtility$getChildren().values()) {
            if (extension(child).viaBedrockUtility$isCubeGroup()) { groups.add(child); watchGroups(child); }
            else children.add(build(child, ancestors));
        }
        Part result = new Part(part, groups.toArray(ModelPart[]::new), children.toArray(Part[]::new));
        parts.put(part, result); ancestors.remove(part); result.request(); return result;
    }
    private void watch(IModelPart part) { part.viaBedrockUtility$watchTopology(topologyChanged); watched.add(part); }
    private void watchGroups(ModelPart group) {
        IModelPart extension = extension(group); watch(extension);
        if (extension.viaBedrockUtility$isEntityRelativeRotation() || extension.viaBedrockUtility$getParentPose() != null
                || extension.viaBedrockUtility$getAbsoluteParentPose() != null) throw new IllegalArgumentException("Dynamic cube-group parent");
        for (ModelPart child : extension.viaBedrockUtility$getChildren().values()) {
            if (!extension(child).viaBedrockUtility$isCubeGroup()) throw new IllegalArgumentException("Author bone inside cube group");
            watchGroups(child);
        }
    }
    Part root() { return root; }
    Part part(ModelPart part) { return parts.get(part); }
    private void releaseParts() {
        for (Part part : parts.values()) part.close();
        parts.clear(); for (IModelPart part : watched) part.viaBedrockUtility$unwatchTopology(topologyChanged);
        watched.clear(); root = null;
    }
    @Override public void close() { if (closed) return; closed = true; releaseParts(); }

    final class Part implements AutoCloseable {
        final ModelPart model;
        final ModelPart[] groups;
        final Part[] children;
        final ModelPart.Cube[] cubes;
        final List<ICuboid> observed = new ArrayList<>();
        final Runnable changed = this::geometryChanged;
        final Runnable upload = this::upload;
        RigidMeshStorage.Shared geometry;
        long epoch = RigidMeshStorage.epoch();
        boolean queued, retired, unsupported, failed;
        int vertexCount;
        Part(ModelPart model, ModelPart[] groups, Part[] children) {
            this.model = model; this.groups = groups; this.children = children;
            cubes = extension(model).viaBedrockUtility$getCuboids().toArray(ModelPart.Cube[]::new);
            try {
                for (ModelPart.Cube cube : cubes) observe(cube);
                for (ModelPart group : groups) observeGroup(group);
            } catch (RuntimeException error) {
                close();
                throw error;
            }
        }
        private void observe(ModelPart.Cube cube) {
            ICuboid extension = (ICuboid)(Object)cube;
            extension.viaBedrockUtility$watchGeometry(changed); observed.add(extension);
        }
        private void observeGroup(ModelPart part) {
            for (ModelPart.Cube cube : extension(part).viaBedrockUtility$getCuboids()) observe(cube);
            for (ModelPart child : extension(part).viaBedrockUtility$getChildren().values()) observeGroup(child);
        }
        private void geometryChanged() {
            RigidMeshStorage.cancel(upload); queued = false;
            release(); unsupported = false; failed = false;
            request();
        }
        void request() {
            if (epoch != RigidMeshStorage.epoch()) { epoch = RigidMeshStorage.epoch(); queued = false; release(); }
            if (retired || closed || queued || unsupported || failed || geometry != null || observed.isEmpty()) return;
            vertexCount = 0;
            for (ICuboid cube : observed) {
                if (!cube.viaBedrockUtility$isVBUCuboid() || cube.viaBedrockUtility$hasAnimatedUv()
                        || cube.viaBedrockUtility$getCompiledGeometry() == null) { unsupported = true; return; }
                vertexCount = Math.addExact(vertexCount, cube.viaBedrockUtility$getCompiledGeometry().vertexCount());
            }
            if (vertexCount == 0) return;
            if (vertexCount > MAX_VERTICES_PER_PART) { unsupported = true; return; }
            queued = RigidMeshStorage.enqueue(upload);
        }
        boolean ready() {
            if (epoch != RigidMeshStorage.epoch()) request();
            if (geometry != null && !geometry.current()) release();
            if (geometry == null) request();
            return geometry != null;
        }
        private void upload() {
            queued = false;
            if (retired || closed || dirty || unsupported || failed) return;
            ByteBuffer buffer = null;
            try {
                buffer = MemoryUtil.memAlloc(Math.multiplyExact(vertexCount, VbuCompiledCuboid.ENTITY_VERTEX_STRIDE));
                long cursor = MemoryUtil.memAddress(buffer); PoseStack pose = new PoseStack();
                for (ModelPart.Cube cube : cubes) cursor = write(cube, pose, cursor);
                for (ModelPart group : groups) cursor = writeGroup(group, pose, cursor);
                if (cursor != MemoryUtil.memAddress(buffer) + buffer.remaining()) throw new IllegalStateException("Rigid mesh vertex count changed during preparation");
                geometry = RigidMeshStorage.acquire(buffer, vertexCount);
                if (geometry == null) unsupported = true; // Keep normal rendering when the shared GPU budget is exhausted.
            } catch (RuntimeException error) {
                failed = true;
                org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge.LOGGER.warn("[RigidMesh] Geometry preparation failed; retaining native vertex submission", error);
            } finally { if (buffer != null) MemoryUtil.memFree(buffer); }
        }
        private long writeGroup(ModelPart part, PoseStack pose, long cursor) {
            pose.pushPose();
            try {
                part.translateAndRotate(pose);
                for (ModelPart.Cube cube : extension(part).viaBedrockUtility$getCuboids()) cursor = write(cube, pose, cursor);
                for (ModelPart child : extension(part).viaBedrockUtility$getChildren().values()) cursor = writeGroup(child, pose, cursor);
                return cursor;
            } finally { pose.popPose(); }
        }
        private long write(ModelPart.Cube cube, PoseStack pose, long pointer) {
            ICuboid extension = (ICuboid)(Object)cube;
            int count = extension.viaBedrockUtility$getCompiledGeometry().writeVertices(pose.last(), pointer,
                    VbuCompiledCuboid.ENTITY_VERTEX_STRIDE, -1, 0, 0, extension.viaBedrockUtility$getVOffset(), false);
            return pointer + (long)count * VbuCompiledCuboid.ENTITY_VERTEX_STRIDE;
        }
        void renderGeometry(PoseStack pose, VertexConsumer consumer, int light, int overlay, int color) {
            for (ModelPart.Cube cube : cubes) cube.compile(pose.last(), consumer, light, overlay, color);
            for (ModelPart group : groups) group.render(pose, consumer, light, overlay, color);
        }
        private void release() { if (geometry != null) { geometry.close(); geometry = null; } }
        @Override public void close() {
            if (retired) return; retired = true; RigidMeshStorage.cancel(upload); queued = false; release();
            for (ICuboid cube : observed) cube.viaBedrockUtility$unwatchGeometry(changed);
            observed.clear();
        }
    }
}
