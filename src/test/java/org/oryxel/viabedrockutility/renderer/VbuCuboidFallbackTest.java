package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VbuCuboidFallbackTest {
    @Test
    void vanillaEmitterPreservesArgbAndDynamicVOffset() {
        final ModelPart.Cube cube = new ModelPart.Cube(
                0, 0, 0, 0, 0, 16, 16, 16,
                0, 0, 0, false, 64, 64, EnumSet.of(Direction.NORTH));
        final RecordingConsumer consumer = new RecordingConsumer();
        final int colorArgb = 0x80402010;
        final int overlay = 0x00020001;
        final int light = 0x00F000F0;
        VbuVanillaCuboidRenderer.render(
                cube.polygons, new PoseStack().last(), consumer, light, overlay, colorArgb,
                0.125F, false, new Vector3f());

        assertEquals(4, consumer.vertices.size());
        for (RecordedVertex vertex : consumer.vertices) {
            assertEquals(colorArgb, vertex.color);
            assertEquals(overlay, vertex.overlay);
            assertEquals(light, vertex.light);
        }
        assertEquals(cube.polygons[0].vertices()[0].v() + 0.125F, consumer.vertices.get(0).v);
    }

    private record RecordedVertex(float x, float y, float z, int color, float u, float v,
                                  int overlay, int light, float normalX, float normalY, float normalZ) {
    }

    private static final class RecordingConsumer implements VertexConsumer {
        private final List<RecordedVertex> vertices = new ArrayList<>();

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v,
                              int overlay, int light, float normalX, float normalY, float normalZ) {
            this.vertices.add(new RecordedVertex(
                    x, y, z, color, u, v, overlay, light, normalX, normalY, normalZ));
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }
}
