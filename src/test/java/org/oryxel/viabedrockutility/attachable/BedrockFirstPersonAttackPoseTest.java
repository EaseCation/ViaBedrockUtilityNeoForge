package org.oryxel.viabedrockutility.attachable;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.oryxel.viabedrockutility.renderer.BedrockPlayerModelMetadata;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BedrockFirstPersonAttackPoseTest {
    private static final float EPSILON = 1.0E-4F;

    @Test
    void attackEndpointsDoNotChangeTheEmptyHandBasePose() {
        assertZero(BedrockFirstPersonView.attackPose(0.0F));
        assertZero(BedrockFirstPersonView.attackPose(1.0F));
        assertZero(BedrockFirstPersonView.attackPose(-1.0F));
        assertZero(BedrockFirstPersonView.attackPose(2.0F));
    }

    @Test
    void attackMidpointMatchesBedrockPlayerResourceFormula() {
        final BedrockFirstPersonView.AttackPose pose = BedrockFirstPersonView.attackPose(0.5F);

        assertEquals(-5.803263F, pose.position().x(), EPSILON);
        assertEquals(-1.7547F, pose.position().y(), EPSILON);
        assertEquals(1.5155F, pose.position().z(), EPSILON);
        assertEquals(-56.3816F, pose.rotation().x(), EPSILON);
        assertEquals(37.5877F, pose.rotation().y(), EPSILON);
        assertEquals(18.7939F, pose.rotation().z(), EPSILON);
    }

    @Test
    void bedrockAttackUsesAnimationAdapterAxes() {
        final BedrockFirstPersonView.AttackPose pose = BedrockFirstPersonView.attackPose(0.5F);
        final Vector3f position = BedrockTransformConvention.bedrockBindingOffsetToJavaModel(
                new Vector3f(pose.position().x(), pose.position().y(), pose.position().z()));
        final Vector3f rotation = BedrockTransformConvention.bedrockAnimationRotationToJavaModel(
                new Vector3f(pose.rotation().x(), pose.rotation().y(), pose.rotation().z()));

        assertEquals(pose.position().x(), position.x, EPSILON);
        assertEquals(-pose.position().y(), position.y, EPSILON);
        assertEquals(pose.position().z(), position.z, EPSILON);
        assertEquals(pose.rotation().x(), rotation.x, EPSILON);
        assertEquals(pose.rotation().y(), rotation.y, EPSILON);
        assertEquals(pose.rotation().z(), rotation.z, EPSILON);
        assertNotEquals(0.0F, rotation.lengthSquared(), EPSILON);
    }

    @Test
    void defaultFirstPersonPoseRemainsTheZeroAttackPath() {
        final BedrockPlayerModelMetadata metadata = new BedrockPlayerModelMetadata(false);
        final BedrockPlayerModelMetadata.Bone arm = new BedrockPlayerModelMetadata.Bone(
                "rightArm", "rightarm", "", "", new Vector3f(-5.0F, 2.0F, 0.0F),
                new Vector3f(), null, Map.of(), null);

        final Vector3f defaultOrigin = BedrockFirstPersonView.STANDARD.localMatrix(metadata, arm)
                .transformPosition(new Vector3f());
        final Vector3f zeroAttackOrigin = BedrockFirstPersonView.STANDARD.localMatrix(metadata, arm, 0.0F)
                .transformPosition(new Vector3f());
        final Vector3f midAttackOrigin = BedrockFirstPersonView.STANDARD.localMatrix(metadata, arm, 0.5F)
                .transformPosition(new Vector3f());

        assertEquals(defaultOrigin.x, zeroAttackOrigin.x, EPSILON);
        assertEquals(defaultOrigin.y, zeroAttackOrigin.y, EPSILON);
        assertEquals(defaultOrigin.z, zeroAttackOrigin.z, EPSILON);
        assertNotEquals(defaultOrigin.x, midAttackOrigin.x, EPSILON);
    }

    private static void assertZero(BedrockFirstPersonView.AttackPose pose) {
        assertEquals(0.0F, pose.position().x(), EPSILON);
        assertEquals(0.0F, pose.position().y(), EPSILON);
        assertEquals(0.0F, pose.position().z(), EPSILON);
        assertEquals(0.0F, pose.rotation().x(), EPSILON);
        assertEquals(0.0F, pose.rotation().y(), EPSILON);
        assertEquals(0.0F, pose.rotation().z(), EPSILON);
    }
}
