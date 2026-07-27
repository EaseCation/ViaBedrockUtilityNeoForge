package org.oryxel.viabedrockutility.renderer;

import net.minecraft.client.renderer.RenderType;

import java.nio.ByteBuffer;
import java.util.List;

public interface FrozenMeshBackend {
    Handle upload(String name, ByteBuffer vertices);

    void drawGroup(RenderType renderType, List<FrozenMeshDrawQueue.DrawRecord> records) throws Exception;

    interface Handle extends AutoCloseable {
        long sizeBytes();

        boolean isClosed();

        @Override
        void close();
    }
}
