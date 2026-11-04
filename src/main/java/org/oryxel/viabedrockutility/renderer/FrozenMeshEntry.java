package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

public final class FrozenMeshEntry extends GpuMeshEntry {
    private final RenderType renderType;
    private final int packedLight;
    private final long poseRevision;

    public FrozenMeshEntry(FrozenMeshBackend.Handle handle, RenderType renderType, int vertexCount,
                           int indexCount, VertexFormat.Mode mode, int packedLight) {
        this(handle, renderType, vertexCount, indexCount, mode, packedLight, 0L);
    }

    public FrozenMeshEntry(FrozenMeshBackend.Handle handle, RenderType renderType, int vertexCount,
                           int indexCount, VertexFormat.Mode mode, int packedLight, long poseRevision) {
        super(handle, vertexCount, indexCount, mode);
        this.renderType = renderType;
        this.packedLight = packedLight;
        this.poseRevision = poseRevision;
    }

    public RenderType renderType() { return renderType; }
    public int packedLight() { return packedLight; }
    public long poseRevision() { return poseRevision; }
}
