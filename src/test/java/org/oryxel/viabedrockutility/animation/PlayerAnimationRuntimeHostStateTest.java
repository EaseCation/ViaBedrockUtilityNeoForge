package org.oryxel.viabedrockutility.animation;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerAnimationRuntimeHostStateTest {
    @Test
    void restoresVanillaTranslationsRotationsAndScaleBeforeBedrockSampling() {
        final PlayerModel model = new PlayerModel(LayerDefinition.create(
                PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot(), false);

        final float initialArmX = model.rightArm.x;
        final float initialArmY = model.rightArm.y;
        final float initialArmZ = model.rightArm.z;
        final float initialBodyY = model.body.y;

        model.rightArm.x = 2.0F;
        model.rightArm.y = 5.2F;
        model.rightArm.z = 4.0F;
        model.rightArm.xRot = 1.2F;
        model.rightArm.yRot = -0.7F;
        model.rightArm.zRot = 0.4F;
        model.rightArm.xScale = 0.75F;
        model.rightArm.yScale = 1.25F;
        model.rightArm.zScale = 0.5F;
        model.body.y = 3.2F;

        PlayerAnimationRuntime.resetVanillaHostPose(model);

        assertEquals(initialArmX, model.rightArm.x, 1.0e-6F);
        assertEquals(initialArmY, model.rightArm.y, 1.0e-6F);
        assertEquals(initialArmZ, model.rightArm.z, 1.0e-6F);
        assertEquals(initialBodyY, model.body.y, 1.0e-6F);
        assertEquals(0.0F, model.rightArm.xRot, 1.0e-6F);
        assertEquals(0.0F, model.rightArm.yRot, 1.0e-6F);
        assertEquals(0.0F, model.rightArm.zRot, 1.0e-6F);
        assertEquals(1.0F, model.rightArm.xScale, 1.0e-6F);
        assertEquals(1.0F, model.rightArm.yScale, 1.0e-6F);
        assertEquals(1.0F, model.rightArm.zScale, 1.0e-6F);
    }
}
