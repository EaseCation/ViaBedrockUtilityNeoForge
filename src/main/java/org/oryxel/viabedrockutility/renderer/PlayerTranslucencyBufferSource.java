package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.resources.ResourceLocation;
import org.oryxel.viabedrockutility.mixin.interfaces.IRenderTypeTexture;

import java.util.Optional;

final class PlayerTranslucencyBufferSource implements MultiBufferSource {
    private static final float ALPHA = 0.3F;

    private final MultiBufferSource delegate;

    PlayerTranslucencyBufferSource(final MultiBufferSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public VertexConsumer getBuffer(final RenderType renderType) {
        return new AlphaVertexConsumer(this.delegate.getBuffer(toTranslucent(renderType)));
    }

    static MultiBufferSource unwrap(final MultiBufferSource source) {
        return source instanceof PlayerTranslucencyBufferSource translucent ? translucent.delegate : source;
    }

    private static RenderType toTranslucent(final RenderType renderType) {
        final Optional<ResourceLocation> texture = renderType instanceof IRenderTypeTexture textureProvider
                ? textureProvider.viaBedrockUtility$getTexture()
                : Optional.empty();
        return toTranslucent(renderType, texture);
    }

    static RenderType toTranslucent(final RenderType renderType, final Optional<ResourceLocation> texture) {
        if (renderType == RenderType.glint() || renderType == RenderType.entityGlint()
                || renderType == RenderType.armorEntityGlint()) {
            return RenderType.glintTranslucent();
        }
        if (renderType == Sheets.solidBlockSheet() || renderType == Sheets.cutoutBlockSheet()) {
            return Sheets.translucentItemSheet();
        }
        if (texture.isPresent()) {
            return RenderType.entityTranslucent(texture.get(), true);
        }
        return renderType;
    }

    private static final class AlphaVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;

        private AlphaVertexConsumer(final VertexConsumer delegate) {
            this.delegate = delegate;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.delegate.setColor(red, green, blue, Math.round(alpha * ALPHA));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            this.delegate.setNormal(normalX, normalY, normalZ);
            return this;
        }
    }
}
