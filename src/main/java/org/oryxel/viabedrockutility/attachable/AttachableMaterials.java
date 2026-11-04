package org.oryxel.viabedrockutility.attachable;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Native Bedrock material semantics that cannot be represented by a full-bright light value.
 * This renderer has no knowledge of weapons, action clocks or cosmetic layer priority.
 */
public abstract class AttachableMaterials extends RenderType {
    // Build from the neutral snippet: Iris copies shader mappings along toBuilder(),
    // which would replace this material with its ordinary solid-entity shader.
    private static final RenderPipeline EMISSIVE_ALPHA = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withSampler("Sampler1")
            .withLocation(ResourceLocation.parse("viabedrockutility:pipeline/entity_emissive_alpha"))
            .withVertexShader(ResourceLocation.parse("viabedrockutility:core/entity_emissive_alpha"))
            .withFragmentShader(ResourceLocation.parse("viabedrockutility:core/entity_emissive_alpha"))
            .withCull(false).build();
    private static final RenderPipeline EMISSIVE_ALPHA_TRANSLUCENT = EMISSIVE_ALPHA.toBuilder()
            .withLocation(ResourceLocation.parse("viabedrockutility:pipeline/entity_emissive_alpha_translucent"))
            .withBlend(BlendFunction.TRANSLUCENT).withDepthWrite(false).build();
    private record Key(ResourceLocation texture, boolean translucent) {}
    private static final Map<Key, RenderType> TYPES = new HashMap<>();

    private AttachableMaterials() { super("vbu_materials", 0, false, false, () -> {}, () -> {}); }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(EMISSIVE_ALPHA);
        event.registerPipeline(EMISSIVE_ALPHA_TRANSLUCENT);
    }

    public static void clear() { TYPES.clear(); }

    public static boolean owns(RenderPipeline pipeline) {
        return pipeline == EMISSIVE_ALPHA || pipeline == EMISSIVE_ALPHA_TRANSLUCENT
                || org.oryxel.viabedrockutility.renderer.RigidMeshMaterials.owns(pipeline);
    }

    static boolean emissiveAlpha(String material) {
        return material.toLowerCase(Locale.ROOT).equals("entity_emissive_alpha");
    }

    static RenderType emissiveAlpha(ResourceLocation texture, boolean translucent) {
        return TYPES.computeIfAbsent(new Key(texture, translucent), key -> create(
                "vbu_entity_emissive_alpha", 1536, true, translucent,
                translucent ? EMISSIVE_ALPHA_TRANSLUCENT : EMISSIVE_ALPHA,
                CompositeState.builder().setTextureState(new TextureStateShard(texture, false))
                        .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).createCompositeState(true)));
    }
}
