package org.oryxel.viabedrockutility.renderer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.oryxel.viabedrockutility.adapter.McBoneModel;
import org.oryxel.viabedrockutility.adapter.ModelPartBoneTarget;
import org.oryxel.viabedrockutility.attachable.AttachableHostContext;
import org.oryxel.viabedrockutility.util.BundledPlayerModelFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 用第三章暗村休息的真实关键帧验证模型、盔甲和视角切换。 */
class BedrockPlayerParentPoseTest {
    private static final float EPSILON = 1.0E-4F;

    @Test
    void allFourRestPosesLowerTheTorsoAndArmorTogether() throws Exception {
        final JsonObject poses;
        try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream(
                "/runelegend-rest-pose.json"), StandardCharsets.UTF_8)) {
            poses = JsonParser.parseReader(reader).getAsJsonObject();
        }
        for (boolean slim : new boolean[]{false, true}) {
            for (String animation : poses.keySet()) {
                final PlayerModel model = BundledPlayerModelFactory.create(slim);
                final BedrockPlayerModelMetadata metadata = BedrockPlayerModelMetadata.get(model);
                final JsonObject bones = poses.getAsJsonObject(animation);
                for (String name : bones.keySet()) {
                    final JsonObject frame = bones.getAsJsonObject(name);
                    final String key = name.replace("Leg", "_leg");
                    final ModelPartBoneTarget target = new ModelPartBoneTarget(metadata.bone(key).part());
                    target.addOffset(vector(frame, "position"));
                    if (frame.has("rotation")) {
                        target.addRotation(vector(frame, "rotation"));
                    }
                }
                BedrockPlayerParentPose.update(model);
                BedrockPlayerArmorPose.update(model);

                final float drop = -vector(bones.getAsJsonObject("waist"), "position").y / 16.0F;
                for (ModelPart part : new ModelPart[]{model.body, model.head, model.leftArm, model.rightArm}) {
                    assertMatrixEquals(new Matrix4f().translation(0, drop, 0), rendered(model.root(), part));
                }
                // 腿已直接挂在 root 下，它自己的下移和旋转只能应用一次。
                for (String name : new String[]{"left_leg", "right_leg"}) {
                    final ModelPart part = metadata.bone(name).part();
                    assertMatrixEquals(BedrockModelPartTransform.current(part), rendered(model.root(), part));
                }

                final HumanoidModel<?> armor = new HumanoidModel<>(LayerDefinition.create(
                        HumanoidModel.createMesh(new CubeDeformation(1.0F), 0), 64, 32).bakeRoot());
                armor.body.copyFrom(model.body);
                assertMatrixEquals(new Matrix4f().translation(0, drop, 0), rendered(armor.body));
                armor.leftLeg.copyFrom(model.leftLeg);
                final Matrix4f expectedLeg = BedrockModelPartTransform.current(model.leftLeg)
                        .translate(armor.leftLeg.getInitialPose().x() / 16.0F,
                                armor.leftLeg.getInitialPose().y() / 16.0F, 0);
                assertMatrixEquals(expectedLeg, rendered(armor.leftLeg));
            }
        }
    }

    @Test
    void waistAndBodyRotationReachArmsWithoutRepeatingRootTransform() {
        final PlayerModel model = BundledPlayerModelFactory.create(false);
        final BedrockPlayerModelMetadata metadata = BedrockPlayerModelMetadata.get(model);
        new ModelPartBoneTarget(model.root()).addRotation(new Vector3f(0, 35, 0));
        new ModelPartBoneTarget(metadata.bone("waist").part()).addOffset(new Vector3f(0, -9, 0));
        new ModelPartBoneTarget(model.body).addRotation(new Vector3f(25, -15, 10));
        new ModelPartBoneTarget(model.rightArm).addRotation(new Vector3f(-20, 0, 8));
        model.body.xScale = 0.7F;
        final Matrix4f expected = BedrockModelPartTransform.current(model.root())
                .mul(BedrockModelPartTransform.current(metadata.bone("waist").part()))
                .mul(BedrockModelPartTransform.current(model.body))
                .mul(BedrockModelPartTransform.current(model.rightArm));
        BedrockPlayerParentPose.update(model);
        assertMatrixEquals(expected, rendered(model.root(), model.rightArm));

        // 隐藏父骨骼时不能对当前零缩放矩阵求逆。
        model.body.xScale = model.body.yScale = model.body.zScale = 0;
        BedrockPlayerParentPose.update(model);
        assertTrue(rendered(model.root(), model.rightArm).isFinite());
    }

    @Test
    void nextSampleClearsThirdPersonPrefixBeforeFirstPersonComposition() {
        final PlayerModel model = BundledPlayerModelFactory.create(false);
        final BedrockPlayerModelMetadata metadata = BedrockPlayerModelMetadata.get(model);
        new ModelPartBoneTarget(metadata.bone("waist").part()).addOffset(new Vector3f(0, -9, 0));
        BedrockPlayerParentPose.update(model);
        assertEquals(9.0F / 16.0F, rendered(model.rightArm).m31(), EPSILON);

        new McBoneModel(model).resetAllBones();
        assertMatrixEquals(new Matrix4f(), rendered(model.rightArm));
        // 第一人称重新采样后仍由原有入口组合父链，部件本身没有第三人称补偿。
        new ModelPartBoneTarget(metadata.bone("waist").part()).addOffset(new Vector3f(0, -3, 0));
        final AttachableHostContext host = new AttachableHostContext(metadata);
        final Matrix4f prefix = host.firstPersonArmRenderPrefix(metadata.bone("right_arm"));
        assertMatrixEquals(prefix, new Matrix4f(prefix).mul(rendered(model.rightArm)));

        new McBoneModel(model).resetAllBones();
        BedrockPlayerParentPose.update(model);
        assertMatrixEquals(new Matrix4f(), rendered(model.root(), model.body));
    }

    private static Vector3f vector(JsonObject object, String key) {
        final var value = object.getAsJsonArray(key);
        return new Vector3f(value.get(0).getAsFloat(), value.get(1).getAsFloat(), value.get(2).getAsFloat());
    }

    private static Matrix4f rendered(ModelPart... parts) {
        final PoseStack stack = new PoseStack();
        for (ModelPart part : parts) {
            part.translateAndRotate(stack);
        }
        return new Matrix4f(stack.last().pose());
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
