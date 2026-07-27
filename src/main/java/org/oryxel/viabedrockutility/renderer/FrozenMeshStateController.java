package org.oryxel.viabedrockutility.renderer;

/** Distance hysteresis kept independent from Minecraft so its edge cases can be unit-tested. */
public final class FrozenMeshStateController {
    private FrozenMeshState state = FrozenMeshState.DYNAMIC;

    public FrozenMeshState state() {
        return state;
    }

    public boolean requiresInitialPose(boolean hasRetainedAnimatedPose) {
        return state != FrozenMeshState.DYNAMIC && !hasRetainedAnimatedPose;
    }

    public FrozenMeshState update(boolean enabled, double distance, double enterDistance, double exitDistance) {
        if (!enabled) {
            state = FrozenMeshState.DYNAMIC;
        } else if (state == FrozenMeshState.DYNAMIC && distance >= enterDistance) {
            state = FrozenMeshState.BAKE_PENDING;
        } else if (state != FrozenMeshState.DYNAMIC && distance <= exitDistance) {
            state = FrozenMeshState.DYNAMIC;
        }
        return state;
    }

    public void bakingComplete() {
        if (state == FrozenMeshState.BAKE_PENDING) {
            state = FrozenMeshState.FROZEN;
        }
    }

    public void requestBake() {
        if (state == FrozenMeshState.FROZEN) {
            state = FrozenMeshState.BAKE_PENDING;
        }
    }

    public void reset() {
        state = FrozenMeshState.DYNAMIC;
    }
}
