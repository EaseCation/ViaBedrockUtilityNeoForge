package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.oryxel.viabedrockutility.mixin.impl.accessor.CompositeRenderTypeAccessor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Keeps one player's original materials together until its private render target is ready. */
public final class PlayerTranslucencyBufferSource implements MultiBufferSource, AutoCloseable {
    private record Batch(ByteBufferBuilder storage, BufferBuilder builder) {
    }

    // Reuse by batch position, not skin RenderType: players entering/leaving view must not retain
    // a native allocation for every texture ever seen by a pooled player slot.
    private final List<ByteBufferBuilder> storage = new ArrayList<>();
    private final Map<RenderType, Batch> builders = new LinkedHashMap<>();
    private MultiBufferSource delegate;
    private double distanceSquared;

    void begin(MultiBufferSource delegate, double distanceSquared) {
        this.delegate = delegate;
        this.distanceSquared = distanceSquared;
    }

    double distanceSquared() {
        return this.distanceSquared;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        if (this.delegate instanceof OutlineBufferSource && renderType.isOutline()) {
            return this.delegate.getBuffer(renderType);
        }
        VertexConsumer body = this.builders.computeIfAbsent(renderType, type -> {
            int index = this.builders.size();
            if (index == this.storage.size()) {
                this.storage.add(new ByteBufferBuilder(8192));
            }
            ByteBufferBuilder buffer = this.storage.get(index);
            return new Batch(buffer, new BufferBuilder(buffer, type.mode(), type.format()));
        }).builder();
        if (this.delegate instanceof OutlineBufferSource) {
            // Asking for the outline RenderType uses OutlineBufferSource's outline-only branch,
            // retaining its team color without also emitting an opaque copy of the player's body.
            return renderType.outline().<VertexConsumer>map(outline ->
                    VertexMultiConsumer.create(this.delegate.getBuffer(outline), body)).orElse(body);
        }
        return body;
    }

    public static MultiBufferSource unwrap(MultiBufferSource source) {
        return source instanceof PlayerTranslucencyBufferSource translucent ? translucent.delegate : source;
    }

    void draw() {
        // Item rendering may request the glint consumer before the base consumer. Its EQUAL depth
        // test needs the base first; normal translucent materials likewise follow opaque geometry.
        for (var entry : this.builders.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> renderOrder(entry.getKey()))).toList()) {
            RenderType type = entry.getKey();
            MeshData mesh = entry.getValue().builder().build();
            if (mesh != null) {
                if (type.sortOnUpload()) {
                    mesh.sortQuads(entry.getValue().storage(), RenderSystem.getProjectionType().vertexSorting());
                }
                type.draw(mesh);
            }
        }
    }

    private static int renderOrder(RenderType type) {
        if (type == RenderType.glint() || type == RenderType.glintTranslucent()
                || type == RenderType.entityGlint() || type == RenderType.armorEntityGlint()) {
            return 2;
        }
        return type instanceof CompositeRenderTypeAccessor accessor
                && accessor.viaBedrockUtility$getRenderPipeline().getBlendFunction().isPresent() ? 1 : 0;
    }

    void reset() {
        this.builders.clear();
        this.storage.forEach(ByteBufferBuilder::clear);
        this.delegate = null;
    }

    @Override
    public void close() {
        this.builders.clear();
        this.storage.forEach(ByteBufferBuilder::close);
        this.storage.clear();
        this.delegate = null;
    }
}
