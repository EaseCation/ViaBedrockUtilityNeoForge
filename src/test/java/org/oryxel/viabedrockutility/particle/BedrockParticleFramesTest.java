package org.oryxel.viabedrockutility.particle;

import net.easecation.beparticle.anchor.ParticleSpaceTransform;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.oryxel.viabedrockutility.attachable.BedrockTransformConvention;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BedrockParticleFramesTest {
    @Test
    void ownerRootPlusRelativeTargetRotationReconstructsJavaViewDirection() {
        assertActorDirection(0.0F, 0.0F, 0.0F);
        assertActorDirection(90.0F, 90.0F, 0.0F);
        assertActorDirection(-90.0F, -45.0F, -20.0F);
        assertActorDirection(170.0F, -170.0F, 35.0F);
        assertActorDirection(-170.0F, 170.0F, -55.0F);
    }

    @Test
    void ownerRootPolicyIsInvariantAcrossPresentationPoses() {
        final Quaternionf ownerRoot = BedrockParticleFrames.ownerRootRotation(37.0F, new Quaternionf());
        final Quaternionf firstPersonPose = new Quaternionf().rotateXYZ(1.2F, -0.4F, 2.1F);
        final Quaternionf thirdPersonPose = new Quaternionf().rotateXYZ(-0.7F, 0.9F, -1.4F);

        final Vector3f localForward = new Vector3f(0.0F, 0.0F, -1.0F);
        final Vector3f first = new Vector3f(localForward).rotate(BedrockParticleFrames.simulationRotation(
                BedrockParticlePlacement.OrientationPolicy.OWNER_ROOT, firstPersonPose, ownerRoot,
                new Quaternionf(), new Quaternionf()));
        final Vector3f third = new Vector3f(localForward).rotate(BedrockParticleFrames.simulationRotation(
                BedrockParticlePlacement.OrientationPolicy.OWNER_ROOT, thirdPersonPose, ownerRoot,
                new Quaternionf(), new Quaternionf()));
        assertVector(first, third);
    }

    @Test
    void targetPosePolicyRetainsLocatorOrientation() {
        final Quaternionf target = new Quaternionf().rotateXYZ(0.4F, -1.1F, 0.2F);
        final Quaternionf owner = BedrockParticleFrames.ownerRootRotation(80.0F, new Quaternionf());
        final Vector3f expected = new Vector3f(0.0F, 0.0F, -1.0F).rotate(target);
        final Vector3f actual = new Vector3f(0.0F, 0.0F, -1.0F).rotate(
                BedrockParticleFrames.simulationRotation(
                        BedrockParticlePlacement.OrientationPolicy.TARGET_POSE, target, owner,
                        new Quaternionf(), new Quaternionf()));
        assertVector(expected, actual);
    }

    @Test
    void ownerViewPolicyUsesHeadYawAndPitchInsteadOfBodyOrPresentationPose() {
        final Quaternionf ownerRoot = BedrockParticleFrames.ownerRootRotation(35.0F, new Quaternionf());
        final Quaternionf ownerView = ParticleSpaceTransform.javaEntityViewRotation(-70.0F, 28.0F);
        final Quaternionf presentation = new Quaternionf().rotateXYZ(1.1F, -0.8F, 0.4F);

        final Vector3f actual = new Vector3f(0.0F, 0.0F, -1.0F).rotate(
                BedrockParticleFrames.simulationRotation(
                        BedrockParticlePlacement.OrientationPolicy.OWNER_VIEW,
                        presentation, ownerRoot, ownerView, new Quaternionf()));
        final Vector3f expected = new Vector3f(0.0F, 0.0F, -1.0F).rotate(ownerView);
        assertVector(expected, actual);
    }

    @Test
    void worldPolicyUsesExplicitWorldOrientation() {
        final Quaternionf world = BedrockParticlePlacement.WorldTrajectory.fromForward(
                new Vector3f(), new Vector3f(1.0F, 2.0F, -3.0F)).orientation();
        final Vector3f actual = new Vector3f(0.0F, 0.0F, -1.0F).rotate(
                BedrockParticleFrames.simulationRotation(
                        BedrockParticlePlacement.OrientationPolicy.WORLD,
                        new Quaternionf().rotateX(1.0F), new Quaternionf().rotateY(2.0F),
                        new Quaternionf().rotateZ(3.0F), world));
        assertVector(new Vector3f(1.0F, 2.0F, -3.0F).normalize(), actual);
    }

    @Test
    void localRotationChangesBasisWithoutOrbitingAnchorOffset() {
        final Vector3f base = new Vector3f(10.0F, 20.0F, 30.0F);
        final Quaternionf frame = new Quaternionf().rotateY((float) Math.toRadians(90.0));
        final Vector3f offset = new Vector3f(0.0F, 0.0F, -2.0F);
        final Quaternionf local = new Quaternionf().rotateX((float) Math.toRadians(70.0));

        final BedrockParticleFrames.PlacedFrame placed =
                BedrockParticleFrames.placeLocalFrame(base, frame, offset, local);
        assertVector(new Vector3f(8.0F, 20.0F, 30.0F), placed.position());
        assertVector(
                new Vector3f(0.0F, 0.0F, -1.0F).rotate(new Quaternionf(frame).mul(local)),
                new Vector3f(0.0F, 0.0F, -1.0F).rotate(placed.rotation()));
    }

    @Test
    void boneBindingAndEntityPacketOffsetsKeepDistinctCoordinateContracts() {
        final Vector3f raw = new Vector3f(-0.38F, -0.2F, 0.1F);
        final UUID owner = UUID.randomUUID();
        final var bone = new BedrockParticlePlacement.BoundEffect(
                owner, BedrockParticlePlacement.TargetKind.BONE, "head", raw,
                new Quaternionf(), BedrockParticlePlacement.ViewContext.FIRST_PERSON);
        final var entity = new BedrockParticlePlacement.BoundEffect(
                owner, BedrockParticlePlacement.TargetKind.ENTITY, "", raw,
                new Quaternionf(), BedrockParticlePlacement.ViewContext.ENTITY);

        assertVector(new Vector3f(-0.38F, 0.2F, 0.1F), BedrockParticleFrames.localOffset(
                bone, BedrockTransformConvention.BindingOffsetFrame.JAVA_MODEL));
        assertVector(new Vector3f(0.38F, 0.2F, 0.1F), BedrockParticleFrames.localOffset(
                bone, BedrockTransformConvention.BindingOffsetFrame.OWNER_ATTACHMENT));
        assertVector(raw, BedrockParticleFrames.localOffset(
                entity, BedrockTransformConvention.BindingOffsetFrame.JAVA_MODEL));
    }

    @Test
    void legacyPoseSnapshotUsesOneBasisForAllContracts() {
        final Quaternionf rotation = new Quaternionf().rotateXYZ(0.2F, 0.4F, -0.6F);
        final BedrockPoseSnapshot snapshot = new BedrockPoseSnapshot(
                new Vector3f(), rotation, new Vector3f(1.0F), new Vector3f(), 4L, true);
        final Vector3f forward = new Vector3f(0.0F, 0.0F, -1.0F);
        assertVector(new Vector3f(forward).rotate(snapshot.rotation()),
                new Vector3f(forward).rotate(snapshot.simulationRotation()));
        assertVector(new Vector3f(forward).rotate(snapshot.rotation()),
                new Vector3f(forward).rotate(snapshot.continuationRotation()));
    }

    private static void assertActorDirection(float bodyYaw, float headYaw, float pitch) {
        final float relativeYaw = BedrockParticleFrames.relativeTargetYaw(headYaw, bodyYaw);
        final double pitchRadians = Math.toRadians(pitch);
        final double yawRadians = Math.toRadians(relativeYaw);
        final Vector3f localDirection = new Vector3f(
                (float) (Math.cos(pitchRadians) * Math.sin(yawRadians)),
                (float) -Math.sin(pitchRadians),
                (float) (-Math.cos(pitchRadians) * Math.cos(yawRadians)));
        final Vector3f actual = localDirection.rotate(
                BedrockParticleFrames.ownerRootRotation(bodyYaw, new Quaternionf()));
        final Vector3f expected = new Vector3f(0.0F, 0.0F, -1.0F)
                .rotate(ParticleSpaceTransform.javaEntityViewRotation(headYaw, pitch));
        assertVector(expected, actual);
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, 1.0E-4F);
        assertEquals(expected.y, actual.y, 1.0E-4F);
        assertEquals(expected.z, actual.z, 1.0E-4F);
    }
}
