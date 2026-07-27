package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

public final class FrozenMeshEntry implements AutoCloseable {
    private final FrozenMeshBackend.Handle handle;
    private final RenderType renderType;
    private final int vertexCount;
    private final int indexCount;
    private final VertexFormat.Mode mode;
    private final int packedLight;
    private boolean valid = true;
    private boolean failed;

    public FrozenMeshEntry(FrozenMeshBackend.Handle handle, RenderType renderType, int vertexCount,
                           int indexCount, VertexFormat.Mode mode, int packedLight) {
        this.handle = handle;
        this.renderType = renderType;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        this.mode = mode;
        this.packedLight = packedLight;
    }

    public FrozenMeshBackend.Handle handle() { return handle; }
    public RenderType renderType() { return renderType; }
    public int vertexCount() { return vertexCount; }
    public int indexCount() { return indexCount; }
    public VertexFormat.Mode mode() { return mode; }
    public int packedLight() { return packedLight; }
    public long sizeBytes() { return handle.sizeBytes(); }
    public boolean isValid() { return valid && !handle.isClosed(); }
    public boolean isFailed() { return failed; }

    public void markFailed() {
        failed = true;
    }

    @Override
    public void close() {
        if (!valid) {
            return;
        }
        valid = false;
        handle.close();
    }
}
