package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import org.oryxel.viabedrockutility.renderer.model.CustomEntityModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.ByteBuffer;

/** Collects frozen draws during entity rendering and replays them before deferred name tags. */
public final class FrozenMeshDrawQueue {
    private static final Map<RenderType, List<DrawRecord>> GROUPS = new LinkedHashMap<>();
    private static FrozenMeshBackend backend = MinecraftFrozenMeshBackend.INSTANCE;

    private FrozenMeshDrawQueue() {
    }

    public static boolean enqueue(FrozenMeshEntry entry, Matrix4f rootPose,
                                  CustomEntityModel<?> fallbackModel, int light, boolean flatLight) {
        if (!entry.isValid() || entry.isFailed()) {
            return false;
        }
        GROUPS.computeIfAbsent(entry.renderType(), ignored -> new ArrayList<>())
                .add(new DrawRecord(entry, new Matrix4f(rootPose), fallbackModel, light, flatLight));
        return true;
    }

    static FrozenMeshBackend.Handle upload(String name, ByteBuffer vertices) {
        return backend.upload(name, vertices);
    }

    public static void flush() {
        try {
            for (Map.Entry<RenderType, List<DrawRecord>> group : GROUPS.entrySet()) {
                List<DrawRecord> records = group.getValue();
                try {
                    backend.drawGroup(group.getKey(), records);
                } catch (Throwable error) {
                    ViaBedrockUtilityNeoForge.LOGGER.warn(
                            "[FrozenMesh] Static draw failed for {}; using dynamic fallback", group.getKey(), error);
                    for (DrawRecord record : records) {
                        if (!record.drawn()) {
                            record.entry().markFailed();
                            FrozenEntityMeshCache.global().remove(record.entry(), "draw_error");
                            drawFallback(record);
                        }
                    }
                    VbuRenderMetrics.recordFrozenFallback("draw_error", records.size());
                }
            }
        } finally {
            GROUPS.clear();
        }
    }

    public static void clear() {
        GROUPS.clear();
    }

    static void setBackendForTesting(FrozenMeshBackend replacement) {
        backend = replacement;
    }

    static int groupCount() {
        return GROUPS.size();
    }

    private static void drawFallback(DrawRecord record) {
        RenderType renderType = record.entry().renderType();
        try (ByteBufferBuilder allocation = new ByteBufferBuilder(renderType.bufferSize())) {
            BufferBuilder builder = new BufferBuilder(allocation, renderType.mode(), renderType.format());
            PoseStack pose = new PoseStack();
            pose.mulPose(record.rootPose());
            VbuCompileScratch.FLAT_NORMAL = record.flatLight();
            record.fallbackModel().renderToBuffer(
                    pose, builder, record.light(), OverlayTexture.pack(0, 10));
            MeshData mesh = builder.build();
            if (mesh != null) {
                renderType.draw(mesh);
            }
        } catch (Throwable fallbackError) {
            ViaBedrockUtilityNeoForge.LOGGER.error(
                    "[FrozenMesh] Dynamic safety fallback also failed", fallbackError);
        } finally {
            VbuCompileScratch.FLAT_NORMAL = false;
        }
    }

    public static final class DrawRecord {
        private final FrozenMeshEntry entry;
        private final Matrix4f rootPose;
        private final CustomEntityModel<?> fallbackModel;
        private final int light;
        private final boolean flatLight;
        private boolean drawn;

        DrawRecord(FrozenMeshEntry entry, Matrix4f rootPose, CustomEntityModel<?> fallbackModel,
                   int light, boolean flatLight) {
            this.entry = entry;
            this.rootPose = rootPose;
            this.fallbackModel = fallbackModel;
            this.light = light;
            this.flatLight = flatLight;
        }

        public FrozenMeshEntry entry() { return entry; }
        public Matrix4f rootPose() { return rootPose; }
        public CustomEntityModel<?> fallbackModel() { return fallbackModel; }
        public int light() { return light; }
        public boolean flatLight() { return flatLight; }
        public boolean drawn() { return drawn; }
        public void markDrawn() { drawn = true; }
    }
}
