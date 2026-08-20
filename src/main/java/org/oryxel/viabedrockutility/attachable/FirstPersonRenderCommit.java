package org.oryxel.viabedrockutility.attachable;

/** Applies a profile's host-mesh policy at most once across all render-controller passes. */
final class FirstPersonRenderCommit {
    private final FirstPersonHostMeshPolicy policy;
    private final FirstPersonHostMeshRenderer renderer;
    private boolean committed;

    FirstPersonRenderCommit(FirstPersonHostMeshPolicy policy, FirstPersonHostMeshRenderer renderer) {
        this.policy = policy;
        this.renderer = renderer;
    }

    void commit() {
        if (committed) {
            return;
        }
        committed = true;
        if (policy == FirstPersonHostMeshPolicy.BOUND_ARM && renderer != null) {
            renderer.renderBoundArm();
        }
    }

    boolean committed() {
        return committed;
    }
}
