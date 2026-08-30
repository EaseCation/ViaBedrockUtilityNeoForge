package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.PartPose;
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

    @Test
    void copiedArmorUsesItsBindPoseAfterTheCompletePlayerDeformation() {
        final ModelPart playerArm = new ModelPart(List.of(), Map.of());
        playerArm.x = 1.25F;
        playerArm.y = 3.2F;
        playerArm.z = 4.0F;
        playerArm.xRot = 0.35F;
        playerArm.yRot = -0.2F;
        playerArm.xScale = 1.25F;

        final Matrix4f deformation = new Matrix4f()
                .translate(0.0F, 0.2F, 0.55F)
                .rotateX((float) Math.toRadians(28.0F));
        BedrockPlayerArmorPose.set(playerArm, deformation);

        final ModelPart armorArm = new ModelPart(List.of(), Map.of());
        final PartPose armorBind = PartPose.offset(-5.0F, 2.0F, 0.0F);
        armorArm.setInitialPose(armorBind);
        armorArm.loadPose(armorBind);
        armorArm.loadPose(playerArm.storePose());
        armorArm.xScale = playerArm.xScale;
        BedrockPlayerArmorPose.copy(playerArm, armorArm);

        final PoseStack actual = new PoseStack();
        BedrockPlayerArmorPose.apply(armorArm, actual);
        actual.translate(armorArm.x / 16.0F, armorArm.y / 16.0F,
                armorArm.z / 16.0F);
        final Matrix4f expected = new Matrix4f(deformation)
                .translate(armorBind.x() / 16.0F, armorBind.y() / 16.0F,
                        armorBind.z() / 16.0F);
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
