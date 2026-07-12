package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VbuCompiledCuboidTest {
    private static final int STRIDE = VbuCompiledCuboid.ENTITY_VERTEX_STRIDE;

    @Test
    void writesEntityLayoutColorUvLightingAndNormalExactly() {
        final ModelPart.Vertex[] vertices = {
                vertex(0, 0, 0, 0.125F, 0.25F),
                vertex(16, 0, 0, 0.375F, 0.25F),
                vertex(16, 16, 0, 0.375F, 0.75F),
                vertex(0, 16, 0, 0.125F, 0.75F)
        };
        final VbuCompiledCuboid geometry = VbuCompiledCuboid.compile(new ModelPart.Polygon[]{
                new ModelPart.Polygon(vertices, new Vector3f(Direction.NORTH.step()))
        });

        assertTrue(geometry.isBox());
        assertEquals(4, geometry.vertexCount());

        final int colorArgb = 0x80402010;
        final int colorAbgr = ARGB.toABGR(colorArgb);
        final int overlay = 0x00020001;
        final int light = 0x00F000F0;
        final ByteBuffer memory = MemoryUtil.memAlloc(geometry.vertexCount() * STRIDE);
        try {
            final long address = MemoryUtil.memAddress(memory);
            geometry.writeVertices(new PoseStack().last(), address, STRIDE, colorAbgr,
                    overlay, light, 0.125F, false);

            assertEquals(0.0F, MemoryUtil.memGetFloat(address));
            assertEquals(0.0F, MemoryUtil.memGetFloat(address + 4));
            assertEquals(0.0F, MemoryUtil.memGetFloat(address + 8));
            assertEquals(colorAbgr, MemoryUtil.memGetInt(address + 12));
            assertEquals(0.125F, MemoryUtil.memGetFloat(address + 16));
            assertEquals(0.375F, MemoryUtil.memGetFloat(address + 20));
            assertEquals(overlay, MemoryUtil.memGetInt(address + 24));
            assertEquals(light, MemoryUtil.memGetInt(address + 28));
            assertEquals(packNormal(0.0F, 0.0F, -1.0F), MemoryUtil.memGetInt(address + 32));

            // Exercise both shifted-UV cache refresh and the zero-offset base-array restoration.
            geometry.writeVertices(new PoseStack().last(), address, STRIDE, colorAbgr,
                    overlay, light, -0.25F, false);
            assertEquals(0.0F, MemoryUtil.memGetFloat(address + 20));
            geometry.writeVertices(new PoseStack().last(), address, STRIDE, colorAbgr,
                    overlay, light, 0.0F, false);
            assertEquals(0.25F, MemoryUtil.memGetFloat(address + 20));
        } finally {
            MemoryUtil.memFree(memory);
        }
    }

    @Test
    void boxBasisTransformMatchesMatrixTransformInVertexOrder() {
        final ModelPart.Vertex[] vertices = {
                vertex(-8, 2, 4, 0, 0),
                vertex(12, 2, 4, 1, 0),
                vertex(12, 18, 4, 1, 1),
                vertex(-8, 18, 4, 0, 1)
        };
        final VbuCompiledCuboid geometry = VbuCompiledCuboid.compile(new ModelPart.Polygon[]{
                new ModelPart.Polygon(vertices, new Vector3f(Direction.SOUTH.step()))
        });
        final PoseStack poses = new PoseStack();
        poses.translate(1.25, -0.5, 2.0);
        poses.mulPose(new Quaternionf().rotationZYX(0.17F, -0.31F, 0.42F));
        poses.scale(1.5F, 0.75F, 2.25F);

        final ByteBuffer memory = MemoryUtil.memAlloc(geometry.vertexCount() * STRIDE);
        try {
            final long address = MemoryUtil.memAddress(memory);
            geometry.writeVertices(poses.last(), address, STRIDE, -1, 0, 0, 0, false);
            for (int i = 0; i < vertices.length; i++) {
                final Vector3f expected = poses.last().pose().transformPosition(
                        vertices[i].pos().x() / 16.0F,
                        vertices[i].pos().y() / 16.0F,
                        vertices[i].pos().z() / 16.0F,
                        new Vector3f());
                final long vertexAddress = address + (long) i * STRIDE;
                assertEquals(expected.x(), MemoryUtil.memGetFloat(vertexAddress), 1.0e-6F);
                assertEquals(expected.y(), MemoryUtil.memGetFloat(vertexAddress + 4), 1.0e-6F);
                assertEquals(expected.z(), MemoryUtil.memGetFloat(vertexAddress + 8), 1.0e-6F);
            }
        } finally {
            MemoryUtil.memFree(memory);
        }
    }

    @Test
    void genericMeshDeduplicatesPositionsAndTransformsNonAxisNormal() {
        final ModelPart.Vertex shared = vertex(8, 4, 2, 0.5F, 0.5F);
        final ModelPart.Vertex[] vertices = {
                vertex(0, 0, 0, 0, 0),
                shared,
                vertex(16, 8, 4, 1, 1),
                shared
        };
        final Vector3f sourceNormal = new Vector3f(1.0F, 2.0F, 3.0F).normalize();
        final VbuCompiledCuboid geometry = VbuCompiledCuboid.compile(new ModelPart.Polygon[]{
                new ModelPart.Polygon(vertices, sourceNormal)
        });
        assertFalse(geometry.isBox(), "three distinct values on an axis must use the generic path");

        final PoseStack poses = new PoseStack();
        poses.scale(2.0F, 3.0F, 4.0F);
        final Vector3f expectedNormal = poses.last().transformNormal(sourceNormal, new Vector3f());
        final ByteBuffer memory = MemoryUtil.memAlloc(geometry.vertexCount() * STRIDE);
        try {
            final long address = MemoryUtil.memAddress(memory);
            geometry.writeVertices(poses.last(), address, STRIDE, -1, 3, 5, 0, false);
            assertEquals(packNormal(expectedNormal.x(), expectedNormal.y(), expectedNormal.z()),
                    MemoryUtil.memGetInt(address + 32));

            final long repeated = address + 3L * STRIDE;
            assertEquals(MemoryUtil.memGetFloat(address + STRIDE), MemoryUtil.memGetFloat(repeated));
            assertEquals(MemoryUtil.memGetFloat(address + STRIDE + 4), MemoryUtil.memGetFloat(repeated + 4));
            assertEquals(MemoryUtil.memGetFloat(address + STRIDE + 8), MemoryUtil.memGetFloat(repeated + 8));

            geometry.writeVertices(poses.last(), address, STRIDE, -1, 3, 5, 0, true);
            assertEquals(VbuCompileScratch.FLAT_PACKED_NORMAL, MemoryUtil.memGetInt(address + 32));
        } finally {
            MemoryUtil.memFree(memory);
        }
    }

    @Test
    void explicitBoxBoundsKeepSharedFaceBytesStableWhenOtherFacesAreMissing() {
        final ModelPart.Polygon sharedFace = new ModelPart.Polygon(new ModelPart.Vertex[]{
                vertex(0, 0, 16, 0, 0),
                vertex(16, 0, 16, 1, 0),
                vertex(16, 16, 16, 1, 1),
                vertex(0, 16, 16, 0, 1)
        }, new Vector3f(Direction.SOUTH.step()));
        final ModelPart.Polygon oppositeFace = new ModelPart.Polygon(new ModelPart.Vertex[]{
                vertex(16, 0, 0, 0, 0),
                vertex(0, 0, 0, 1, 0),
                vertex(0, 16, 0, 1, 1),
                vertex(16, 16, 0, 0, 1)
        }, new Vector3f(Direction.NORTH.step()));
        final VbuCompiledCuboid missingFaces = VbuCompiledCuboid.compileBox(
                new ModelPart.Polygon[]{sharedFace}, 0, 0, 0, 16, 16, 16);
        final VbuCompiledCuboid completeBounds = VbuCompiledCuboid.compileBox(
                new ModelPart.Polygon[]{sharedFace, oppositeFace}, 0, 0, 0, 16, 16, 16);

        final PoseStack poses = new PoseStack();
        poses.translate(0.3, -0.7, 1.1);
        poses.mulPose(new Quaternionf().rotationZYX(0.23F, 0.41F, -0.37F));
        final ByteBuffer first = MemoryUtil.memAlloc(missingFaces.vertexCount() * STRIDE);
        final ByteBuffer second = MemoryUtil.memAlloc(completeBounds.vertexCount() * STRIDE);
        try {
            missingFaces.writeVertices(poses.last(), MemoryUtil.memAddress(first), STRIDE,
                    0x80102040, 7, 11, 0, false);
            completeBounds.writeVertices(poses.last(), MemoryUtil.memAddress(second), STRIDE,
                    0x80102040, 7, 11, 0, false);
            for (int offset = 0; offset < 4 * STRIDE; offset++) {
                assertEquals(first.get(offset), second.get(offset), "byte offset " + offset);
            }
        } finally {
            MemoryUtil.memFree(first);
            MemoryUtil.memFree(second);
        }
    }

    private static ModelPart.Vertex vertex(float x, float y, float z, float u, float v) {
        return new ModelPart.Vertex(x, y, z, u, v);
    }

    private static int packNormal(float x, float y, float z) {
        return normalByte(x) | (normalByte(y) << 8) | (normalByte(z) << 16);
    }

    private static int normalByte(float value) {
        return (byte) (Mth.clamp(value, -1.0F, 1.0F) * 127.0F) & 0xFF;
    }
}
