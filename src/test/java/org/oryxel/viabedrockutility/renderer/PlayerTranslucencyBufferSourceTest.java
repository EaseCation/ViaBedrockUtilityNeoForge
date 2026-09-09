package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PlayerTranslucencyBufferSourceTest {

    @Test
    void convertsTexturedGeometryAndMultipliesVertexAlpha() {
        final ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("test", "player.png");
        final CapturingBufferSource delegate = new CapturingBufferSource();
        final PlayerTranslucencyBufferSource translucent = new PlayerTranslucencyBufferSource(delegate);

        assertEquals(RenderType.entityTranslucent(texture, true),
                PlayerTranslucencyBufferSource.toTranslucent(
                        RenderType.entityCutoutNoCull(texture), Optional.of(texture)));

        translucent.getBuffer(RenderType.glint()).setColor(10, 20, 30, 255);

        assertEquals(RenderType.glintTranslucent(), delegate.renderType);
        assertEquals(77, delegate.consumer.alpha);
    }

    @Test
    void unwrapRestoresTheOriginalSourceForNameTags() {
        final CapturingBufferSource delegate = new CapturingBufferSource();

        assertSame(delegate, PlayerTranslucencyBufferSource.unwrap(
                new PlayerTranslucencyBufferSource(delegate)));
        assertSame(delegate, PlayerTranslucencyBufferSource.unwrap(delegate));
    }

    private static final class CapturingBufferSource implements MultiBufferSource {
        private final CapturingVertexConsumer consumer = new CapturingVertexConsumer();
        private RenderType renderType;

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            this.renderType = renderType;
            return this.consumer;
        }
    }

    private static final class CapturingVertexConsumer implements VertexConsumer {
        private int alpha;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.alpha = alpha;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
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
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            return this;
        }
    }
}
