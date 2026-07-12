package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;

import java.lang.reflect.InvocationTargetException;

/**
 * Sodium-free facade for the optional bulk writer. The implementation class name is deliberately a string so
 * loading this class is safe when Sodium is absent.
 */
public final class SodiumPushBackend {
    private static final String SODIUM_API_CLASS =
            "net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter";
    private static final String IMPLEMENTATION_CLASS =
            "org.oryxel.viabedrockutility.renderer.SodiumVertexPushBackend";

    private static final VbuVertexPushBackend BACKEND = loadBackend();

    private SodiumPushBackend() {
    }

    public static boolean isSupported() {
        return BACKEND != null;
    }

    public static int stride() {
        return BACKEND != null ? BACKEND.vertexStride() : 0;
    }

    public static Object tryOf(VertexConsumer consumer) {
        return BACKEND != null ? BACKEND.tryOf(consumer) : null;
    }

    public static void push(Object writer, long pointer, int vertexCount) {
        if (BACKEND == null) {
            throw new IllegalStateException("Sodium vertex push backend is unavailable");
        }
        BACKEND.push(writer, pointer, vertexCount);
    }

    private static VbuVertexPushBackend loadBackend() {
        final ClassLoader loader = SodiumPushBackend.class.getClassLoader();
        try {
            // Avoid loading and verifying the implementation class when Sodium is simply not installed.
            Class.forName(SODIUM_API_CLASS, false, loader);
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (LinkageError | RuntimeException error) {
            logIncompatibleBackend(error);
            return null;
        }

        try {
            final Class<?> implementation = Class.forName(IMPLEMENTATION_CLASS, true, loader);
            return (VbuVertexPushBackend) implementation.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            final Throwable cause = error instanceof InvocationTargetException invocation && invocation.getCause() != null
                    ? invocation.getCause()
                    : error;
            logIncompatibleBackend(cause);
            return null;
        }
    }

    private static void logIncompatibleBackend(Throwable error) {
        ViaBedrockUtilityNeoForge.LOGGER.warn(
                "Sodium is present, but its public entity vertex API is incompatible; using vanilla vertex emission",
                error);
    }
}
