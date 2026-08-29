package org.oryxel.viabedrockutility.attachable;

import net.easecation.bedrockmotion.render.RenderControllerEvaluator;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;

class AttachableRenderTypesTest {
    @Test
    void bedrockAlphaTestUsesNoCullForReversedTextureMeshes() {
        final ResourceLocation texture = ResourceLocation.parse("minecraft:textures/items/wood_sword");
        final RenderControllerEvaluator.EvaluatedRenderPass pass =
                new RenderControllerEvaluator.EvaluatedRenderPass(
                        "default", null, "geometry.sword", texture.toString(),
                        Map.of(), Map.of(), false, true,
                        RenderControllerEvaluator.BlendMode.OPAQUE, false, false, 1.0F, 0xFFFFFFFF);

        assertSame(RenderType.entityCutoutNoCull(texture),
                AttachableRenderTypes.renderType("entity_alphatest", pass, texture));
    }

    @Test
    void evaluatedDoubleSidedPassUsesNoCullInDefaultAttachablePath() {
        final ResourceLocation texture = ResourceLocation.parse("minecraft:textures/items/wood_sword");
        final RenderControllerEvaluator.EvaluatedRenderPass pass =
                new RenderControllerEvaluator.EvaluatedRenderPass(
                        "default", null, "geometry.sword", texture.toString(),
                        Map.of(), Map.of(), false, false,
                        RenderControllerEvaluator.BlendMode.ALPHA_TEST, false, false, 1.0F, 0xFFFFFFFF);

        assertSame(RenderType.entityCutoutNoCull(texture),
                AttachableRenderTypes.renderType(pass, texture));
    }
}
