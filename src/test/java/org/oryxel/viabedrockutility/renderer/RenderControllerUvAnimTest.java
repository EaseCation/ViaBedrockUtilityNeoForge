package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.easecation.bedrockmotion.mocha.MoLangEngine;
import org.cube.converter.data.bedrock.controller.BedrockRenderController;
import org.junit.jupiter.api.Test;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.Value;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class RenderControllerUvAnimTest {
    private static final float EPSILON = 1.0E-6F;

    @Test
    void appliesEvaluatedAffineUvAndReusesConsumer() {
        final RenderControllerUvAnim uvAnim = RenderControllerUvAnim.create(controller(
                new BedrockRenderController.UvAnim("(math.mod(v.lo, 51) * 20 + 1) / 1024", "0.5", "18 / 1024", "7 / 256")));
        final MutableObjectBinding variables = new MutableObjectBinding();
        final Scope scope = MoLangEngine.newScope();
        scope.set("variable", variables);
        scope.set("v", variables);
        variables.set("lo", Value.of(52));
        uvAnim.update(scope);

        final RecordingConsumer sink = new RecordingConsumer();
        final VertexConsumer wrapped = uvAnim.wrap(sink);
        wrapped.addVertex(0, 0, 0, 0xFFFFFFFF, 0.5F, 1.0F, 0, 0, 0, 1, 0);
        assertEquals(21.0F / 1024 + 0.5F * 18 / 1024, sink.u, EPSILON);
        assertEquals(0.5F + 7.0F / 256, sink.v, EPSILON);

        wrapped.setUv(1.0F, 0.0F);
        assertEquals(21.0F / 1024 + 18.0F / 1024, sink.u, EPSILON);
        assertEquals(0.5F, sink.v, EPSILON);
        assertSame(wrapped, uvAnim.wrap(new RecordingConsumer()));
    }

    @Test
    void keepsLastValueWhenEvaluationIsSkipped() {
        final RenderControllerUvAnim uvAnim = RenderControllerUvAnim.create(controller(
                new BedrockRenderController.UvAnim("v.frame * 0.25", "0", "0.25", "1")));
        final MutableObjectBinding variables = new MutableObjectBinding();
        final Scope scope = MoLangEngine.newScope();
        scope.set("v", variables);
        variables.set("frame", Value.of(2));
        uvAnim.update(scope);

        final RecordingConsumer sink = new RecordingConsumer();
        uvAnim.wrap(sink).setUv(0.0F, 0.0F);
        assertEquals(0.5F, sink.u, EPSILON);
        // 非动画帧不调用 update，沿用上一帧的偏移
        variables.set("frame", Value.of(3));
        uvAnim.wrap(sink).setUv(1.0F, 1.0F);
        assertEquals(0.75F, sink.u, EPSILON);
        assertEquals(1.0F, sink.v, EPSILON);
    }

    @Test
    void identityAndAbsentUvAnimDoNotWrap() {
        final RecordingConsumer sink = new RecordingConsumer();
        final RenderControllerUvAnim identity = RenderControllerUvAnim.create(controller(
                new BedrockRenderController.UvAnim("0", "0", "1", "1")));
        assertSame(sink, identity.wrap(sink));
        assertNull(RenderControllerUvAnim.create(controller(null)));
    }

    private static BedrockRenderController controller(BedrockRenderController.UvAnim uvAnim) {
        return new BedrockRenderController("controller.render.test", Map.of(), "Geometry.default",
                List.of("Texture.default"), List.of(), List.of(), List.of(), Map.of(), Map.of(),
                false, 1.0F, uvAnim);
    }

    private static final class RecordingConsumer implements VertexConsumer {
        private float u;
        private float v;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v,
                              int overlay, int light, float nx, float ny, float nz) {
            this.u = u;
            this.v = v;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }
}
