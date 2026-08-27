package org.oryxel.viabedrockutility.renderer;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BedrockModelPartTransformTest {
    private static final float EPSILON = 1.0E-4F;

    @Test
    void animatedPositionPrecedesRotationAndNonUniformScale() {
        Vector3f pivot = new Vector3f(2.0F, 9.0F, -1.0F);
        Vector3f offset = new Vector3f(4.0F, -6.0F, 3.0F);
        Vector3f rotation = new Vector3f(35.0F, -20.0F, 12.0F);
        float xRot = 0.15F;
        float yRot = -0.25F;
        float zRot = 0.05F;
        float xScale = 0.6F;
        float yScale = 0.75F;
        float zScale = 1.2F;
        Matrix4f expected = new Matrix4f()
                .translate(4.0F / 16.0F, -6.0F / 16.0F, 3.0F / 16.0F)
                .translate(2.0F / 16.0F, 9.0F / 16.0F, -1.0F / 16.0F)
                .rotateZYX((float) Math.toRadians(12.0F),
                        (float) Math.toRadians(-20.0F),
                        (float) Math.toRadians(35.0F))
                .rotateZYX(zRot, yRot, xRot)
                .scale(xScale, yScale, zScale)
                .translate(-2.0F / 16.0F, -9.0F / 16.0F, 1.0F / 16.0F);

        Matrix4f actual = BedrockModelPartTransform.compose(pivot, offset, rotation,
                0.0F, 0.0F, 0.0F, xRot, yRot, zRot,
                xScale, yScale, zScale, false);

        assertMatrixEquals(expected, actual);
    }

    @Test
    void scaleDoesNotChangeAnimatedPosition() {
        Matrix4f actual = BedrockModelPartTransform.compose(
                new Vector3f(), new Vector3f(4.0F, -6.0F, 3.0F), new Vector3f(),
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                0.6F, 0.75F, 1.2F, false);
        Vector3f origin = actual.transformPosition(new Vector3f());

        assertEquals(4.0F / 16.0F, origin.x, EPSILON);
        assertEquals(-6.0F / 16.0F, origin.y, EPSILON);
        assertEquals(3.0F / 16.0F, origin.z, EPSILON);
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
