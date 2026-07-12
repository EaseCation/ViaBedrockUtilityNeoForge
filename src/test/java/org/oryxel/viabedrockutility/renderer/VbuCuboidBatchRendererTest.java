package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.oryxel.viabedrockutility.mixin.interfaces.ICuboid;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class VbuCuboidBatchRendererTest {
    @Test
    void twoCuboidsWithIndependentVOffsetsUseOnePushAndKeepOrder() throws ClassNotFoundException {
        assumeTrue(Boolean.parseBoolean(System.getProperty("vbu.testSodium", "false")));

        final VbuCompiledCuboid first = quadAtX(0.0F);
        final VbuCompiledCuboid second = quadAtX(32.0F);
        final ICuboid[] states = {new TestCuboid(first, 0.125F), new TestCuboid(second, -0.25F)};
        final VbuCuboidBatchRenderer.Batch batch = new VbuCuboidBatchRenderer.Batch(
                states, new VbuCompiledCuboid[]{first, second}, 8);

        final ClassLoader loader = getClass().getClassLoader();
        final Class<?> writerApi = Class.forName(
                "net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter", true, loader);
        final AtomicInteger pushes = new AtomicInteger();
        final AtomicInteger pushedVertices = new AtomicInteger();
        final AtomicReference<byte[]> bytes = new AtomicReference<>();
        final AtomicReference<VertexConsumer> consumerReference = new AtomicReference<>();
        final Object proxy = Proxy.newProxyInstance(loader, new Class<?>[]{VertexConsumer.class, writerApi},
                (instance, method, arguments) -> switch (method.getName()) {
                    case "canUseIntrinsics" -> true;
                    case "push" -> {
                        pushes.incrementAndGet();
                        final long source = (long) arguments[1];
                        final int count = (int) arguments[2];
                        pushedVertices.set(count);
                        assertFalse(VbuCuboidBatchRenderer.tryRender(
                                new PoseStack().last(), consumerReference.get(), batch,
                                0, 0, -1, false), "nested writers must not reuse the in-flight native buffer");
                        final byte[] copy = new byte[count * VbuCompiledCuboid.ENTITY_VERTEX_STRIDE];
                        MemoryUtil.memByteBuffer(source, copy.length).get(copy);
                        bytes.set(copy);
                        yield null;
                    }
                    case "hashCode" -> System.identityHashCode(instance);
                    case "equals" -> instance == arguments[0];
                    case "toString" -> "BatchTestWriter";
                    default -> instance;
                });
        consumerReference.set((VertexConsumer) proxy);

        assertTrue(VbuCuboidBatchRenderer.tryRender(
                new PoseStack().last(), (VertexConsumer) proxy, batch,
                0x00F000F0, 0x00020001, 0x80402010, false));
        assertEquals(1, pushes.get());
        assertEquals(8, pushedVertices.get());

        final byte[] emitted = bytes.get();
        assertEquals(0.0F, readFloat(emitted, 0));
        assertEquals(2.0F, readFloat(emitted, 4 * VbuCompiledCuboid.ENTITY_VERTEX_STRIDE));
        assertEquals(0.125F, readFloat(emitted, 20));
        assertEquals(-0.25F, readFloat(emitted, 4 * VbuCompiledCuboid.ENTITY_VERTEX_STRIDE + 20));
    }

    private static VbuCompiledCuboid quadAtX(float x) {
        final ModelPart.Vertex[] vertices = {
                new ModelPart.Vertex(x, 0, 0, 0, 0),
                new ModelPart.Vertex(x, 16, 0, 1, 0),
                new ModelPart.Vertex(x, 16, 16, 1, 1),
                new ModelPart.Vertex(x, 0, 16, 0, 1)
        };
        return VbuCompiledCuboid.compile(new ModelPart.Polygon[]{
                new ModelPart.Polygon(vertices, new Vector3f(Direction.WEST.step()))
        });
    }

    private static float readFloat(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).getFloat(offset);
    }

    private static final class TestCuboid implements ICuboid {
        private final VbuCompiledCuboid geometry;
        private float vOffset;

        private TestCuboid(VbuCompiledCuboid geometry, float vOffset) {
            this.geometry = geometry;
            this.vOffset = vOffset;
        }

        @Override public boolean viaBedrockUtility$isVBUCuboid() { return true; }
        @Override public void viaBedrockUtility$markAsVBU() { }
        @Override public void viaBedrockUtility$markAsVBUBox(
                float x0, float y0, float z0, float x1, float y1, float z1) { }
        @Override public void viaBedrockUtility$rebuildCompiledGeometry() { }
        @Override public VbuCompiledCuboid viaBedrockUtility$getCompiledGeometry() { return this.geometry; }
        @Override public float viaBedrockUtility$getVOffset() { return this.vOffset; }
        @Override public void viaBedrockUtility$setVOffset(float offset) { this.vOffset = offset; }
    }
}
