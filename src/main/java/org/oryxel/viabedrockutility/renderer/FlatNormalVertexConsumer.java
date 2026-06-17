package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Wraps a {@link VertexConsumer} and forces every vertex normal to point straight up (0, 1, 0).
 *
 * <p>Minecraft's entity shader shades each face by {@code dot(normal, directional light)} on top of
 * the lightmap, producing the familiar top-bright / bottom-dark gradient. Bedrock's render-controller
 * {@code ignore_lighting} (and emissive materials) expect a flat, fully-bright look. Setting
 * {@link net.minecraft.client.renderer.LightTexture#FULL_BRIGHT} only maxes the lightmap; it does not
 * touch the normal-based directional shading. Both vanilla diffuse light directions favour +Y, so
 * forcing all normals to up gives every face the same maximal shading — combined with FULL_BRIGHT the
 * model renders flat and bright like Bedrock.
 *
 * <p>{@code ModelPart.Cube.compile} feeds vertices through the {@code addVertex(..., nx, ny, nz)}
 * default overload, which routes the normal through {@link #setNormal(float, float, float)}, so
 * overriding that one method is sufficient. The wrapper only rewrites normals on the way into the
 * buffer: it is fully compatible with {@code MultiBufferSource} batching and touches no global render
 * state, so it affects only the model it wraps.
 */
public class FlatNormalVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;

    public FlatNormalVertexConsumer(final VertexConsumer delegate) {
        this.delegate = delegate;
    }

    @Override
    public VertexConsumer addVertex(final float x, final float y, final float z) {
        this.delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(final int red, final int green, final int blue, final int alpha) {
        this.delegate.setColor(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer setUv(final float u, final float v) {
        this.delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(final int u, final int v) {
        this.delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(final int u, final int v) {
        this.delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(final float normalX, final float normalY, final float normalZ) {
        // Flatten: ignore the real normal, always point up so every face gets the brightest shading.
        this.delegate.setNormal(0.0F, 1.0F, 0.0F);
        return this;
    }
}
