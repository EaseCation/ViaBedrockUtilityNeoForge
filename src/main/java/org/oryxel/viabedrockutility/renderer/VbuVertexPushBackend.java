package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Optional bulk vertex writer. Implementations must not leak optional API types through this boundary. */
public interface VbuVertexPushBackend {
    int vertexStride();

    Object tryOf(VertexConsumer consumer);

    void push(Object writer, long pointer, int vertexCount);
}
