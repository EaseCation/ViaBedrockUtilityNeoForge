package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.easecation.bedrockmotion.mocha.MoLangEngine;
import org.cube.converter.data.bedrock.controller.BedrockRenderController;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import team.unnamed.mocha.runtime.Scope;

import java.io.IOException;
import java.util.OptionalDouble;

/**
 * Render controller {@code uv_anim}: uv' = offset + uv * scale in normalized texture space.
 * Expressions are compiled once per model instance; non-constant components are re-evaluated only on
 * frames that run the animation scope (same cadence as part_visibility), otherwise the last value is kept.
 * The affine transform is applied at native vertex submission like AttachableSurfaces, so GPU repeat
 * keeps interpolation across triangles and no per-vertex modulo is introduced.
 */
public final class RenderControllerUvAnim {
    private static final int OFFSET_U = 0;
    private static final int OFFSET_V = 1;
    private static final int SCALE_U = 2;
    private static final int SCALE_V = 3;

    /**
     * Null entries are constant components already folded into {@link #values}.
     */
    private final MoLangEngine.CompiledExpression[] dynamic;
    private final float[] values = {0.0F, 0.0F, 1.0F, 1.0F};
    private final UvVertexConsumer consumer = new UvVertexConsumer();
    private boolean failureReported;

    private RenderControllerUvAnim(MoLangEngine.CompiledExpression[] dynamic) {
        this.dynamic = dynamic;
    }

    /**
     * @return null when the controller has no uv_anim or it cannot be compiled.
     */
    public static RenderControllerUvAnim create(BedrockRenderController controller) {
        if (controller == null || controller.uvAnim() == null) {
            return null;
        }
        final BedrockRenderController.UvAnim source = controller.uvAnim();
        final String[] sources = {source.offsetU(), source.offsetV(), source.scaleU(), source.scaleV()};
        final MoLangEngine.CompiledExpression[] dynamic = new MoLangEngine.CompiledExpression[sources.length];
        final RenderControllerUvAnim uvAnim = new RenderControllerUvAnim(dynamic);
        try {
            for (int i = 0; i < sources.length; i++) {
                final MoLangEngine.CompiledExpression compiled = MoLangEngine.compileForEvaluation(sources[i]);
                final OptionalDouble constant = compiled.constantNumber();
                if (constant.isPresent()) {
                    uvAnim.values[i] = (float) constant.getAsDouble();
                } else {
                    dynamic[i] = compiled;
                }
            }
        } catch (IOException | RuntimeException e) {
            ViaBedrockUtilityNeoForge.LOGGER.warn("[Render] Failed to parse uv_anim of {}", controller.identifier(), e);
            return null;
        }
        return uvAnim;
    }

    void update(Scope scope) {
        for (int i = 0; i < this.dynamic.length; i++) {
            final MoLangEngine.CompiledExpression expression = this.dynamic[i];
            if (expression == null) {
                continue;
            }
            try {
                this.values[i] = (float) MoLangEngine.eval(scope, expression).getAsNumber();
            } catch (RuntimeException e) {
                // Keep the previous value so a failed frame does not jump back to the atlas origin.
                if (!this.failureReported) {
                    this.failureReported = true;
                    ViaBedrockUtilityNeoForge.LOGGER.debug("[Render] uv_anim evaluation failed", e);
                }
            }
        }
    }

    /**
     * Returns a consumer reused across frames; valid until the next call on this instance (render thread only).
     */
    VertexConsumer wrap(VertexConsumer parent) {
        final float[] v = this.values;
        if (v[OFFSET_U] == 0.0F && v[OFFSET_V] == 0.0F && v[SCALE_U] == 1.0F && v[SCALE_V] == 1.0F) {
            return parent;
        }
        return this.consumer.bind(parent, v[OFFSET_U], v[OFFSET_V], v[SCALE_U], v[SCALE_V]);
    }

    /**
     * Not a Sodium VertexBufferWriter, so cuboids take the per-vertex path through setUv/addVertex.
     */
    private static final class UvVertexConsumer implements VertexConsumer {
        private VertexConsumer parent;
        private float offsetU;
        private float offsetV;
        private float scaleU;
        private float scaleV;

        VertexConsumer bind(VertexConsumer parent, float offsetU, float offsetV, float scaleU, float scaleV) {
            this.parent = parent;
            this.offsetU = offsetU;
            this.offsetV = offsetV;
            this.scaleU = scaleU;
            this.scaleV = scaleV;
            return this;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.parent.addVertex(x, y, z);
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v,
                              int overlay, int light, float nx, float ny, float nz) {
            this.parent.addVertex(x, y, z, color, this.offsetU + u * this.scaleU, this.offsetV + v * this.scaleV,
                    overlay, light, nx, ny, nz);
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            this.parent.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.parent.setUv(this.offsetU + u * this.scaleU, this.offsetV + v * this.scaleV);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.parent.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.parent.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.parent.setNormal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer misc(VertexFormatElement element, int... values) {
            this.parent.misc(element, values);
            return this;
        }
    }
}
