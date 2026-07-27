package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FrozenMeshDrawQueueTest {
    @AfterEach
    void restoreBackend() {
        FrozenMeshDrawQueue.clear();
        FrozenMeshDrawQueue.setBackendForTesting(MinecraftFrozenMeshBackend.INSTANCE);
    }

    @Test
    void groupsInFirstUseOrderAndClearsEveryFrame() {
        List<RenderType> groups = new ArrayList<>();
        List<Float> translations = new ArrayList<>();
        FrozenMeshDrawQueue.setBackendForTesting(new FrozenMeshBackend() {
            @Override public Handle upload(String name, ByteBuffer vertices) { throw new UnsupportedOperationException(); }
            @Override public void drawGroup(RenderType renderType, List<FrozenMeshDrawQueue.DrawRecord> records) {
                groups.add(renderType);
                records.forEach(record -> translations.add(record.rootPose().m30()));
                records.forEach(FrozenMeshDrawQueue.DrawRecord::markDrawn);
            }
        });
        RenderType solid = RenderType.entitySolid(ResourceLocation.withDefaultNamespace("stone"));
        RenderType cutout = RenderType.entityCutout(ResourceLocation.withDefaultNamespace("oak_leaves"));

        Matrix4f submittedPose = new Matrix4f().translation(4.0F, 0.0F, 0.0F);
        assertTrue(FrozenMeshDrawQueue.enqueue(entry(solid), submittedPose, null, 0, false));
        submittedPose.translation(99.0F, 0.0F, 0.0F);
        assertTrue(FrozenMeshDrawQueue.enqueue(entry(cutout), new Matrix4f(), null, 0, false));
        assertTrue(FrozenMeshDrawQueue.enqueue(entry(solid), new Matrix4f(), null, 0, false));
        assertEquals(2, FrozenMeshDrawQueue.groupCount());

        FrozenMeshDrawQueue.flush();

        assertEquals(List.of(solid, cutout), groups);
        assertEquals(4.0F, translations.getFirst());
        assertEquals(0, FrozenMeshDrawQueue.groupCount());
    }

    private static FrozenMeshEntry entry(RenderType renderType) {
        return new FrozenMeshEntry(new FrozenMeshBackend.Handle() {
            private boolean closed;
            @Override public long sizeBytes() { return 1024; }
            @Override public boolean isClosed() { return closed; }
            @Override public void close() { closed = true; }
        }, renderType, 128, 192, VertexFormat.Mode.QUADS, 0);
    }
}
