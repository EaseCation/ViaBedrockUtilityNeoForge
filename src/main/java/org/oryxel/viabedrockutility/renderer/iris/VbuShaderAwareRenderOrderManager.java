package org.oryxel.viabedrockutility.renderer.iris;

import net.irisshaders.batchedentityrendering.impl.TransparencyType;
import net.irisshaders.batchedentityrendering.impl.ordering.GraphTranslucencyRenderOrderManager;
import net.irisshaders.batchedentityrendering.impl.ordering.SimpleRenderOrderManager;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.renderer.RenderType;

import java.util.ArrayList;
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
 *   <li><b>Shaders off</b> — routes every call to a cheap {@link SimpleRenderOrderManager} (insertion order,
 *       zero digraph/DFS). This matches vanilla's behaviour, i.e. exactly what the user sees with Iris
 *       removed, which they confirmed looks correct.</li>
 * </ul>
 *
 * <p>The choice is latched once per flush cycle (first call after {@link #reset()}) so a mid-frame shader
 * toggle can never split one cycle across two managers, and it is re-evaluated every cycle so runtime shader
 * toggles are honoured (the buffer source, hence this object, is constructed only once).</p>
 */
public final class VbuShaderAwareRenderOrderManager extends GraphTranslucencyRenderOrderManager {
    private final SimpleRenderOrderManager simple = new SimpleRenderOrderManager();

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
            this.simple.begin(renderType);
        } else {
            super.begin(renderType);
        }
    }

    @Override
    public void startGroup() {
        if (useSimple()) {
            this.simple.startGroup();
        } else {
            super.startGroup();
        }
    }

    @Override
    public boolean maybeStartGroup() {
        return useSimple() ? this.simple.maybeStartGroup() : super.maybeStartGroup();
    }

    @Override
    public boolean isInGroup() {
        return useSimple() ? this.simple.isInGroup() : super.isInGroup();
    }

    @Override
    public void endGroup() {
        if (useSimple()) {
            this.simple.endGroup();
        } else {
            super.endGroup();
        }
    }

    @Override
    public void resetType(TransparencyType transparencyType) {
        if (useSimple()) {
            this.simple.resetType(transparencyType);
        } else {
            super.resetType(transparencyType);
        }
    }

    @Override
    public List<RenderType> getRenderOrder() {
        // FullyBufferedMultiBufferSource.removeReady() calls clear() on this list, so it MUST be mutable.
        // GraphTranslucencyRenderOrderManager (super) already returns a fresh mutable list; but
        // SimpleRenderOrderManager returns an immutable List.copyOf(...), so copy it into a mutable list to
        // honour the same contract (a crash-on-clear otherwise — UnsupportedOperationException).
        if (useSimple()) {
            return new ArrayList<>(this.simple.getRenderOrder());
        }
        return super.getRenderOrder();
    }

    @Override
    public void reset() {
        if (useSimple()) {
            this.simple.reset();
        } else {
            super.reset();
        }
        // End of this flush cycle — clear the latch so shader state is re-checked next cycle.
        this.useSimple = null;
    }
}
