package org.oryxel.viabedrockutility.particle;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BedrockParticlePlacementTest {
    @Test
    void worldTrajectoryMapsBedrockNegativeZToCallerForward() {
        assertForward(new Vector3f(1, 0, 0));
        assertForward(new Vector3f(-1, 0, 0));
        assertForward(new Vector3f(0, 1, 0));
        assertForward(new Vector3f(0, -1, 0));
        assertForward(new Vector3f(0, 0, 1));
        assertForward(new Vector3f(0, 0, -1));
        assertForward(new Vector3f(2, 3, -4));
    }

    @Test
    void boundPlacementOwnsItsOffsetSnapshot() {
        final Vector3f mutable = new Vector3f(1, 2, 3);
        final var placement = new BedrockParticlePlacement.BoundEffect(
                UUID.randomUUID(), BedrockParticlePlacement.TargetKind.BONE, "head", mutable,
                null, BedrockParticlePlacement.ViewContext.FIRST_PERSON);
        mutable.zero();
        assertVector(new Vector3f(1, 2, 3), placement.localOffset());
        final Vector3f returned = placement.localOffset();
        returned.set(9, 9, 9);
        assertNotEquals(returned, placement.localOffset());
        assertEquals(BedrockParticlePlacement.OrientationPolicy.OWNER_ROOT,
                placement.orientationPolicy());
    }

    @Test
    void locatorDefaultsToItsCompleteTargetPose() {
        final var placement = new BedrockParticlePlacement.BoundEffect(
                UUID.randomUUID(), BedrockParticlePlacement.TargetKind.LOCATOR, "muzzle",
                new Vector3f(), null, BedrockParticlePlacement.ViewContext.THIRD_PERSON);
        assertEquals(BedrockParticlePlacement.OrientationPolicy.TARGET_POSE,
                placement.orientationPolicy());
    }

    @Test
    void originalBoundEffectConstructorRemainsAvailable() throws NoSuchMethodException {
        assertNotNull(BedrockParticlePlacement.BoundEffect.class.getConstructor(
                UUID.class, BedrockParticlePlacement.TargetKind.class, String.class,
                Vector3f.class, Quaternionf.class, BedrockParticlePlacement.ViewContext.class));
    }

    @Test
    void legacyPositionBuilderRemainsAStaticWorldPlacement() {
        final BedrockParticleRequest request = BedrockParticleRequest.builder("test:effect")
                .position(4, 5, 6).build();
        assertEquals(BedrockParticlePlacement.Semantic.WORLD_TRAJECTORY,
                request.placement().semantic());
        assertEquals(4, request.x(), 0.0F);
        assertEquals(5, request.y(), 0.0F);
        assertEquals(6, request.z(), 0.0F);
    }

    private static void assertForward(Vector3f expected) {
        final var placement = BedrockParticlePlacement.WorldTrajectory.fromForward(
                new Vector3f(), expected);
        final Vector3f actual = new Vector3f(0, 0, -1).rotate(placement.orientation()).normalize();
        assertVector(new Vector3f(expected).normalize(), actual);
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, 1.0E-5F);
        assertEquals(expected.y, actual.y, 1.0E-5F);
        assertEquals(expected.z, actual.z, 1.0E-5F);
    }
}
