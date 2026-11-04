package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.VertexFormat;

/** GPU geometry allocation, independent of actors, materials and sampled poses. */
public class GpuMeshEntry implements AutoCloseable {
    private final FrozenMeshBackend.Handle handle;
    private final int vertexCount, indexCount;
    private final VertexFormat.Mode mode;
    private boolean valid = true, failed;

    public GpuMeshEntry(FrozenMeshBackend.Handle handle, int vertexCount, int indexCount, VertexFormat.Mode mode) {
        this.handle = handle;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        this.mode = mode;
    }
    public FrozenMeshBackend.Handle handle() { return handle; }
    public int vertexCount() { return vertexCount; }
    public int indexCount() { return indexCount; }
    public VertexFormat.Mode mode() { return mode; }
    public long sizeBytes() { return handle.sizeBytes(); }
    public boolean isValid() { return valid && !handle.isClosed(); }
    public boolean isFailed() { return failed; }
    public void markFailed() { failed = true; }
    @Override public void close() {
        if (!valid) return;
        valid = false;
        handle.close();
    }
}
