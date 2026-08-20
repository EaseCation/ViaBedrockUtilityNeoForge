package org.oryxel.viabedrockutility.attachable;

import net.easecation.bedrockmotion.render.RenderControllerEvaluator;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/** Maps evaluated render passes and Bedrock material names to vanilla render types. */
final class AttachableRenderTypes {

    private AttachableRenderTypes() {
    }

    static RenderType renderType(RenderControllerEvaluator.EvaluatedRenderPass pass,
                                 ResourceLocation texture) {
        if ((pass.colorArgb() >>> 24) < 255) {
            return RenderType.entityTranslucent(texture);
        }
        return switch (pass.blendMode()) {
            case ALPHA_BLEND -> RenderType.entityTranslucent(texture);
            case ALPHA_TEST -> pass.cull() ? RenderType.entityCutout(texture) : RenderType.entityCutoutNoCull(texture);
            case OPAQUE -> pass.cull() ? RenderType.entitySolid(texture) : RenderType.entityCutoutNoCull(texture);
        };
    }

    static RenderType renderType(String material,
                                 RenderControllerEvaluator.EvaluatedRenderPass pass,
                                 ResourceLocation texture) {
        final String name = material.toLowerCase(Locale.ROOT);
        if ((pass.colorArgb() >>> 24) < 255 || name.contains("blend") || name.contains("spectator")) {
            return RenderType.entityTranslucent(texture);
        }
        final boolean cull = !name.contains("no_cull") && !name.contains("double_sided");
        if (name.contains("alpha_test") || name.contains("alphatest")
                || name.contains("enchanted") || name.contains("glint")) {
            return cull ? RenderType.entityCutout(texture) : RenderType.entityCutoutNoCull(texture);
        }
        return cull ? RenderType.entitySolid(texture) : RenderType.entityCutoutNoCull(texture);
    }
}
