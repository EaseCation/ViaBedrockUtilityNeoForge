package org.oryxel.viabedrockutility.renderer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrozenMeshStateControllerTest {
    @Test
    void appliesEnterExitHysteresis() {
        FrozenMeshStateController controller = new FrozenMeshStateController();

        assertEquals(FrozenMeshState.DYNAMIC, controller.update(true, 17.99, 18.0, 15.0));
        assertEquals(FrozenMeshState.BAKE_PENDING, controller.update(true, 18.0, 18.0, 15.0));
        controller.bakingComplete();
        assertEquals(FrozenMeshState.FROZEN, controller.state());
        assertEquals(FrozenMeshState.FROZEN, controller.update(true, 15.01, 18.0, 15.0));
        assertEquals(FrozenMeshState.DYNAMIC, controller.update(true, 15.0, 18.0, 15.0));
    }

    @Test
    void disablingAlwaysReturnsToDynamic() {
        FrozenMeshStateController controller = new FrozenMeshStateController();
        controller.update(true, 30.0, 18.0, 15.0);
        controller.bakingComplete();

        assertEquals(FrozenMeshState.DYNAMIC, controller.update(false, 30.0, 18.0, 15.0));
    }

    @Test
    void farEntityRequiresOneRetainedAnimatedPoseBeforeFreezing() {
        FrozenMeshStateController controller = new FrozenMeshStateController();

        controller.update(true, 30.0, 18.0, 15.0);
        assertTrue(controller.requiresInitialPose(false));
        assertFalse(controller.requiresInitialPose(true));
        controller.bakingComplete();
        assertTrue(controller.requiresInitialPose(false),
                "a model arriving after the pending frame must still initialize its pose");
        assertEquals(FrozenMeshState.DYNAMIC, controller.update(true, 10.0, 18.0, 15.0));
        assertFalse(controller.requiresInitialPose(false));
    }
}
