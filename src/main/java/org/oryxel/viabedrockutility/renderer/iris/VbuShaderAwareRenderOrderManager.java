package org.oryxel.viabedrockutility.renderer.iris;

import net.irisshaders.batchedentityrendering.impl.BlendingStateHolder;
import net.irisshaders.batchedentityrendering.impl.TransparencyType;
import net.irisshaders.batchedentityrendering.impl.WrappableRenderType;
import net.irisshaders.batchedentityrendering.impl.ordering.GraphTranslucencyRenderOrderManager;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.renderer.RenderType;

import java.util.List;

/**
 * Shader-aware render-order manager for Iris' batched entity rendering.
 *
 * <p>Iris' {@link net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource} hard-codes a
 * {@link GraphTranslucencyRenderOrderManager} — which, on every {@code endBatch()}, builds a render-type
 * dependency digraph and runs a feedback-arc-set DFS to compute a translucency-safe order. ImmediatelyFast
 * calls {@code endBatch()} once per {@code text_display} hologram, so in a lobby full of holograms this
 * solver dominates the render thread (~12% in JFR) even though no shaderpack is active.</p>
 *
 * <p>This class is swapped in via a NEW redirect in that constructor (see
 * {@code mixin.iris.FullyBufferedMultiBufferSourceMixin}). It <b>extends</b> the graph manager purely so the
 * redirect stays type-safe. Behaviour:</p>
 * <ul>
 *   <li><b>Shaderpack active</b> — delegates to {@code super} (the full graph ordering), preserving Iris'
 *       translucency correctness under shaders.</li>
 *   <li><b>Shaders off</b> — uses cheap insertion order within Iris' transparency buckets, then emits buckets
 *       in Iris' canonical order. This avoids the digraph/DFS while keeping opaque geometry ahead of decals
 *       such as armor glint, whose equal-depth test requires the armor depth to exist first.</li>
 * </ul>
 *
 * <p>The choice is latched once per flush cycle (first call after {@link #reset()}) so a mid-frame shader
 * toggle can never split one cycle across two managers, and it is re-evaluated every cycle so runtime shader
 * toggles are honoured (the buffer source, hence this object, is constructed only once).</p>
 */
public final class VbuShaderAwareRenderOrderManager extends GraphTranslucencyRenderOrderManager {
    private final StableRenderTypeBuckets<RenderType> simple =
            new StableRenderTypeBuckets<>(TransparencyType.values().length);

    /** null = undecided for the current cycle; TRUE = shaders off (use simple); FALSE = use graph (super). */
    private Boolean useSimple;

    private boolean useSimple() {
        Boolean s = this.useSimple;
        if (s == null) {
            s = !IrisApi.getInstance().isShaderPackInUse();
            this.useSimple = s;
        }
        return s;
    }

    @Override
    public void begin(RenderType renderType) {
        if (useSimple()) {
            this.simple.add(transparencyType(renderType).ordinal(), renderType);
        } else {
            super.begin(renderType);
        }
    }

    @Override
    public void startGroup() {
        if (!useSimple()) {
            super.startGroup();
        }
    }

    @Override
    public boolean maybeStartGroup() {
        return !useSimple() && super.maybeStartGroup();
    }

    @Override
    public boolean isInGroup() {
        return !useSimple() && super.isInGroup();
    }

    @Override
    public void endGroup() {
        if (!useSimple()) {
            super.endGroup();
        }
    }

    @Override
    public void resetType(TransparencyType transparencyType) {
        if (useSimple()) {
            this.simple.clear(transparencyType.ordinal());
        } else {
            super.resetType(transparencyType);
        }
    }

    @Override
    public List<RenderType> getRenderOrder() {
        if (useSimple()) {
            // FullyBufferedMultiBufferSource.removeReady() clears this list, so ordered() returns a fresh,
            // mutable ArrayList rather than exposing the bucket storage.
            return this.simple.ordered();
        }
        return super.getRenderOrder();
    }

    @Override
    public void reset() {
        if (useSimple()) {
            this.simple.clear();
        } else {
            super.reset();
        }
        // End of this flush cycle — clear the latch so shader state is re-checked next cycle.
        this.useSimple = null;
    }

    private static TransparencyType transparencyType(RenderType renderType) {
        while (renderType instanceof WrappableRenderType wrapped) {
            renderType = wrapped.unwrap();
        }
        if (renderType instanceof BlendingStateHolder holder) {
            return holder.getTransparencyType();
        }
        return TransparencyType.GENERAL_TRANSPARENT;
    }
}
