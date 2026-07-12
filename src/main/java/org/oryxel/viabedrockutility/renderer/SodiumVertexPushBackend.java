package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.EntityVertex;
import org.lwjgl.system.MemoryStack;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Sodium-backed implementation, loaded reflectively only after the public Sodium API is present. */
public final class SodiumVertexPushBackend implements VbuVertexPushBackend {
    public SodiumVertexPushBackend() {
        validateEntityLayout();
    }

    @Override
    public int vertexStride() {
        return EntityVertex.STRIDE;
    }

    @Override
    public Object tryOf(VertexConsumer consumer) {
        return VertexBufferWriter.tryOf(consumer);
    }

    @Override
    public void push(Object writer, long pointer, int vertexCount) {
        // Wrapping consumers may allocate copies from this stack, even though BufferBuilder itself does not.
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ((VertexBufferWriter) writer).push(stack, pointer, vertexCount, EntityVertex.FORMAT);
        }
    }

    private static void validateEntityLayout() {
        validateWriterApi();
        final VertexFormat format = EntityVertex.FORMAT;
        require(format == DefaultVertexFormat.NEW_ENTITY, "EntityVertex.FORMAT is not NEW_ENTITY");
        require(EntityVertex.STRIDE == VbuCompiledCuboid.ENTITY_VERTEX_STRIDE,
                "unexpected EntityVertex stride: " + EntityVertex.STRIDE);
        require(format.getVertexSize() == EntityVertex.STRIDE,
                "EntityVertex stride does not match its format");
        requireOffset(format, VertexFormatElement.POSITION, 0);
        requireOffset(format, VertexFormatElement.COLOR, 12);
        requireOffset(format, VertexFormatElement.UV0, 16);
        requireOffset(format, VertexFormatElement.UV1, 24);
        requireOffset(format, VertexFormatElement.UV2, 28);
        requireOffset(format, VertexFormatElement.NORMAL, 32);
    }

    private static void validateWriterApi() {
        try {
            final Method tryOf = VertexBufferWriter.class.getMethod("tryOf", VertexConsumer.class);
            require(Modifier.isStatic(tryOf.getModifiers()), "VertexBufferWriter.tryOf is not static");
            require(tryOf.getReturnType() == VertexBufferWriter.class,
                    "unexpected VertexBufferWriter.tryOf return type");

            final Method push = VertexBufferWriter.class.getMethod(
                    "push", MemoryStack.class, long.class, int.class, VertexFormat.class);
            require(!Modifier.isStatic(push.getModifiers()), "VertexBufferWriter.push is static");
            require(push.getReturnType() == void.class, "unexpected VertexBufferWriter.push return type");
        } catch (NoSuchMethodException error) {
            throw new IllegalStateException("unexpected VertexBufferWriter API", error);
        }
    }

    private static void requireOffset(VertexFormat format, VertexFormatElement element, int expected) {
        final int actual = format.getOffset(element);
        require(actual == expected,
                "unexpected " + element + " offset: " + actual + " (expected " + expected + ")");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
