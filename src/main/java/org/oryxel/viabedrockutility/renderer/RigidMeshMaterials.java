package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.oryxel.viabedrockutility.mixin.impl.accessor.CompositeRenderTypeAccessor;
import org.oryxel.viabedrockutility.renderer.iris.RigidMeshIris;
import java.util.*;

/** Native material adaptation; no weapon identifiers, clocks or gameplay parameters. */
public final class RigidMeshMaterials {
    // Keep the proven 36-byte geometry layout, but do not advertise NEW_ENTITY to Iris. Its entity
    // format extension contains per-draw metadata that must never be frozen into a shared mesh.
    private static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION).padding(4)
            .add("UV0", VertexFormatElement.UV0).padding(8)
            .add("Normal", VertexFormatElement.NORMAL).padding(1).build();
    private static final boolean IRIS = ModList.get().isLoaded("iris");
    private static final Map<RenderPipeline, RenderPipeline> PIPELINES = new IdentityHashMap<>();
    private static final Set<RenderPipeline> OWNED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<RenderPipeline> FAILED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static Object irisPipeline;
    private static boolean shaders;
    private RigidMeshMaterials() {}
    static void refresh() {
        Object token = IRIS ? RigidMeshIris.pipeline() : null;
        boolean enabled = IRIS && RigidMeshIris.active();
        if (token != irisPipeline || enabled != shaders) {
            clear(); irisPipeline = token; shaders = enabled;
        }
    }
    static RenderPipeline pipeline(RenderType type, boolean glint) {
        refresh();
        if (glint || type.isOutline() || !(type instanceof CompositeRenderTypeAccessor accessor)
                || (IRIS && RigidMeshIris.shadow())) return null;
        RenderPipeline source = accessor.viaBedrockUtility$getRenderPipeline();
        boolean emissiveAlpha = source.getFragmentShader().toString().equals("viabedrockutility:core/entity_emissive_alpha");
        if (FAILED.contains(source) || source.getBlendFunction().isPresent() || source.getStencilTest().isPresent()
                || source.getShaderDefines().flags().contains("APPLY_TEXTURE_MATRIX")
                || (shaders && !emissiveAlpha)
                || (!emissiveAlpha && !source.getFragmentShader().toString().equals("minecraft:core/entity"))) return null;
        return PIPELINES.computeIfAbsent(source, original -> {
            var builder = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                    .withLocation(ResourceLocation.fromNamespaceAndPath("viabedrockutility", "pipeline/rigid_" + PIPELINES.size()))
                    .withVertexShader(ResourceLocation.fromNamespaceAndPath("viabedrockutility", "core/rigid_mesh"))
                    .withFragmentShader(original.getFragmentShader())
                    .withSampler("Sampler1").withUniform("RigidTransforms", UniformType.UNIFORM_BUFFER)
                    .withVertexFormat(FORMAT, VertexFormat.Mode.QUADS)
                    .withCull(original.isCull()).withDepthTestFunction(original.getDepthTestFunction())
                    .withDepthWrite(original.isWriteDepth()).withColorWrite(original.isWriteColor(), original.isWriteAlpha());
            original.getShaderDefines().flags().forEach(builder::withShaderDefine);
            original.getShaderDefines().values().forEach((name, value) -> builder.withShaderDefine(name, Float.parseFloat(value)));
            RenderPipeline result = builder.build();
            if (emissiveAlpha) OWNED.add(result);
            return result;
        });
    }
    static void failed(RenderType type) {
        if (type instanceof CompositeRenderTypeAccessor accessor) FAILED.add(accessor.viaBedrockUtility$getRenderPipeline());
    }
    public static boolean owns(RenderPipeline pipeline) { return OWNED.contains(pipeline); }
    public static void clear() { PIPELINES.clear(); OWNED.clear(); FAILED.clear(); }
}
