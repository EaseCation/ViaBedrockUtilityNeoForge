package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BedrockPlayerArmorPoseTest {
    private static final float EPSILON = 1.0e-5F;

    @Test
    void armorReceivesTheCompleteFlattenedPlayerParentTransform() {
        final ModelPart playerArm = new ModelPart(List.of(), Map.of());
        final ModelPart armor = new ModelPart(List.of(), Map.of());
        final Matrix4f expected = new Matrix4f()
                .rotateX((float) Math.toRadians(35.0F))
                .rotateY((float) Math.toRadians(10.0F))
                .translate(0.25F, -0.5F, 0.75F);
        BedrockPlayerArmorPose.set(playerArm, expected);
        BedrockPlayerArmorPose.copy(playerArm, armor);
        final PoseStack actual = new PoseStack();
        BedrockPlayerArmorPose.apply(armor, actual);

        assertMatrixEquals(expected, actual.last().pose());
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
