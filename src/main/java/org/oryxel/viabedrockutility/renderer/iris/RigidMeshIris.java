package org.oryxel.viabedrockutility.renderer.iris;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.shadows.ShadowRenderingState;

/** Loaded only when Iris is present; no optional Iris types escape this adapter. */
public final class RigidMeshIris {
    private RigidMeshIris() {}
    public static Object pipeline() { return Iris.getPipelineManager().getPipelineNullable(); }
    public static boolean active() { return IrisApi.getInstance().isShaderPackInUse(); }
    public static boolean shadow() { return ShadowRenderingState.areShadowsCurrentlyBeingRendered(); }
}
