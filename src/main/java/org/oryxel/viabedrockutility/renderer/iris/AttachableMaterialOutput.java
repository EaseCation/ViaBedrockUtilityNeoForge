package org.oryxel.viabedrockutility.renderer.iris;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shadows.ShadowRenderingState;

/** Optional Iris output routing for VBU-owned pixel semantics. Never replaces the shader program.
 * Iris owns flipped targets and their lifetime; VBU neither caches FBO IDs nor invents pack slots.
 */
public final class AttachableMaterialOutput {
    private AttachableMaterialOutput() {}

    public static void bind() {
        var pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (!(pipeline instanceof IrisRenderingPipeline iris) || !iris.shouldOverrideShaders()) return;
        if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) iris.bindDefaultShadow();
        else iris.bindDefault();
    }
}
