package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SodiumRuntimeProfileTest {
    private static final String SODIUM_API =
            "net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter";
    private static final String SODIUM_API_RESOURCE =
            "net/caffeinemc/mods/sodium/api/vertex/buffer/VertexBufferWriter.class";
    private static final String BACKEND_FACADE =
            "org.oryxel.viabedrockutility.renderer.SodiumPushBackend";

    @Test
    void runtimeClasspathMatchesSelectedSodiumProfile() {
        final boolean expected = Boolean.parseBoolean(System.getProperty("vbu.testSodium", "false"));
        final ClassLoader loader = SodiumRuntimeProfileTest.class.getClassLoader();

        assertEquals(expected, isPresent(SODIUM_API, loader),
                "Use -PvbuTestSodium=true to run the Sodium integration profile");
        if (!expected) {
            assertNull(loader.getResource(SODIUM_API_RESOURCE),
                    "The default test runtime must not contain Sodium classes");
        }
    }

    @Test
    void sodiumFreeFacadeLoadsAndFallsBackWhenSodiumIsAbsent() throws ReflectiveOperationException {
        final boolean expected = Boolean.parseBoolean(System.getProperty("vbu.testSodium", "false"));

        final Class<?> facade = Class.forName(
                BACKEND_FACADE, true, SodiumRuntimeProfileTest.class.getClassLoader());
        final boolean supported = (boolean) facade.getMethod("isSupported").invoke(null);
        assertEquals(expected, supported,
                "Loading the optional backend facade without Sodium must select the vanilla fallback");
    }

    @Test
    void sodiumPushProvidesUsableMemoryStackToWrappingWriters() throws ClassNotFoundException {
        assumeTrue(Boolean.parseBoolean(System.getProperty("vbu.testSodium", "false")));

        final ClassLoader loader = SodiumRuntimeProfileTest.class.getClassLoader();
        final Class<?> writerApi = Class.forName(SODIUM_API, true, loader);
        final AtomicInteger copiedColor = new AtomicInteger();
        final Object proxy = Proxy.newProxyInstance(loader, new Class<?>[]{VertexConsumer.class, writerApi},
                (instance, method, arguments) -> switch (method.getName()) {
                    case "canUseIntrinsics" -> true;
                    case "push" -> {
                        final MemoryStack stack = (MemoryStack) arguments[0];
                        assertNotNull(stack);
                        final long source = (long) arguments[1];
                        final int count = (int) arguments[2];
                        final long copy = stack.nmalloc(count * VbuCompiledCuboid.ENTITY_VERTEX_STRIDE);
                        MemoryUtil.memCopy(source, copy,
                                (long) count * VbuCompiledCuboid.ENTITY_VERTEX_STRIDE);
                        copiedColor.set(MemoryUtil.memGetInt(copy + 12));
                        yield null;
                    }
                    case "hashCode" -> System.identityHashCode(instance);
                    case "equals" -> instance == arguments[0];
                    case "toString" -> "TestVertexBufferWriter";
                    default -> instance;
                });

        final Object writer = SodiumPushBackend.tryOf((VertexConsumer) proxy);
        assertSame(proxy, writer);
        final ByteBuffer memory = MemoryUtil.memAlloc(VbuCompiledCuboid.ENTITY_VERTEX_STRIDE);
        try {
            final long address = MemoryUtil.memAddress(memory);
            MemoryUtil.memPutInt(address + 12, 0x80102040);
            SodiumPushBackend.push(writer, address, 1);
            assertEquals(0x80102040, copiedColor.get());
        } finally {
            MemoryUtil.memFree(memory);
        }
    }

    private static boolean isPresent(String className, ClassLoader loader) {
        try {
            Class.forName(className, false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
