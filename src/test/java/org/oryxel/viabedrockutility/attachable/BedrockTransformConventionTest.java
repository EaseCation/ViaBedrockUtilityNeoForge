package org.oryxel.viabedrockutility.attachable;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockTransformConventionTest {
    private static final float EPSILON = 1.0E-4F;

    @Test
    void convertsBedrockYExactlyOnce() {
        Vector3f origin = BedrockTransformConvention.bedrockPixelsToRenderSpace()
                .transformPosition(new Vector3f(0, 0, 0));
        Vector3f presentationOrigin = BedrockTransformConvention.bedrockPixelsToRenderSpace()
                .transformPosition(new Vector3f(0,
                        BedrockTransformConvention.PLAYER_PRESENTATION_ORIGIN_Y, 0));

        assertEquals(BedrockTransformConvention.PLAYER_PRESENTATION_ORIGIN_Y / 16.0F,
                origin.y, EPSILON);
        assertEquals(0.0F, presentationOrigin.y, EPSILON);
    }

    @Test
    void hostBindPoseProducesIdentityDeformation() {
        Matrix4f bind = BedrockTransformConvention.deformation(
                new Vector3f(2, 3, 4), new Vector3f(), new Vector3f(10, 20, 30), new Vector3f(1));
        Matrix4f deformation = BedrockTransformConvention.hostDeformation(bind, bind);

        assertMatrixEquals(new Matrix4f(), deformation);
    }

    @Test
    void hostOffsetIsNotReducedToTheBonePivot() {
        Matrix4f bind = BedrockTransformConvention.deformation(
                new Vector3f(8, 16, 0), new Vector3f(), new Vector3f(), new Vector3f(1));
        Matrix4f current = BedrockTransformConvention.deformation(
                new Vector3f(8, 16, 0), new Vector3f(16, 0, 0), new Vector3f(0, 90, 0), new Vector3f(1));
        Matrix4f deformation = BedrockTransformConvention.hostDeformation(current, bind);
        Vector3f point = deformation.transformPosition(new Vector3f(0, 0, 0));

        assertEquals(1.5F, point.x, EPSILON);
        assertEquals(0.0F, point.y, EPSILON);
        assertEquals(0.5F, point.z, EPSILON);
    }

    @Test
    void attachmentKeepsBindAnchorWhenDeformationIsIdentity() {
        Matrix4f bind = new Matrix4f();
        Vector3f rightItemPivot = new Vector3f(-6.0F, 9.016F, 1.0F);

        Vector3f anchor = BedrockTransformConvention.hostAttachment(bind, bind, rightItemPivot)
                .transformPosition(new Vector3f());

        assertEquals(-6.0F / 16.0F, anchor.x, EPSILON);
        assertEquals(9.016F / 16.0F, anchor.y, EPSILON);
        assertEquals(1.0F / 16.0F, anchor.z, EPSILON);
    }

    @Test
    void blockbenchPreviewVectorsUseTheirOwnCoordinateBridge() {
        Vector3f position = BedrockTransformConvention.blockbenchVectorToJavaModel(
                new Vector3f(-13.5F, -10.0F, 12.0F));
        Vector3f rotation = BedrockTransformConvention.blockbenchRotationToJavaModel(
                new Vector3f(-95.0F, 45.0F, 115.0F));

        assertEquals(13.5F, position.x, EPSILON);
        assertEquals(10.0F, position.y, EPSILON);
        assertEquals(12.0F, position.z, EPSILON);
        assertEquals(95.0F, rotation.x, EPSILON);
        assertEquals(-45.0F, rotation.y, EPSILON);
        assertEquals(115.0F, rotation.z, EPSILON);
    }

    @Test
    void boneBoundEffectOffsetsRespectTheResolvedAnchorFrame() {
        Vector3f modelOffset = BedrockTransformConvention.bedrockBindingOffsetToJavaModel(
                new Vector3f(-0.38F, -0.2F, 0.1F));
        Vector3f ownerOffset = BedrockTransformConvention.bedrockBindingOffsetToOwnerAttachment(
                new Vector3f(-0.38F, -0.2F, 0.1F));

        assertEquals(-0.38F, modelOffset.x, EPSILON);
        assertEquals(0.2F, modelOffset.y, EPSILON);
        assertEquals(0.1F, modelOffset.z, EPSILON);
        assertEquals(0.38F, ownerOffset.x, EPSILON);
        assertEquals(0.2F, ownerOffset.y, EPSILON);
        assertEquals(0.1F, ownerOffset.z, EPSILON);
    }

    @Test
    void cubeRotationUsesItsAbsolutePivot() {
        Matrix4f cube = BedrockTransformConvention.deformation(
                new Vector3f(16, 0, 0), new Vector3f(), new Vector3f(0, 0, 90), new Vector3f(1));
        Vector3f point = cube.transformPosition(new Vector3f(2, 0, 0));

        assertEquals(1.0F, point.x, EPSILON);
        assertEquals(1.0F, point.y, EPSILON);
        assertEquals(0.0F, point.z, EPSILON);
    }

    @Test
    void blockbenchCameraFacesPositiveEditorZIntoNegativeRenderZ() {
        Vector3f pointInFront = BedrockFirstPersonView.STANDARD.cameraMatrix()
                .transformPosition(new Vector3f(0.0F,
                        (BedrockTransformConvention.PLAYER_PRESENTATION_ORIGIN_Y - 27.41F) / 16.0F,
                        12.0F / 16.0F));

        assertTrue(pointInFront.z < 0.0F);
    }

    private static void assertMatrixEquals(Matrix4f expected, Matrix4f actual) {
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                assertEquals(expected.get(column, row), actual.get(column, row), EPSILON,
                        "matrix[" + column + "," + row + "]");
            }
        }
    }
}
