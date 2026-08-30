package org.oryxel.viabedrockutility.attachable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.oryxel.viabedrockutility.renderer.BedrockPlayerModelMetadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerHostSharedPosePathTest {
    @Test
    void bedrockCameraSpacePreservesVanillaHandRebound() {
        final float viewPitch = 67.0F;
        final float pitchBob = 4.0F;
        final float viewYaw = -123.0F;
        final float yawBob = 7.0F;
        final PoseStack poses = new PoseStack();
        poses.mulPose(Axis.XP.rotationDegrees((viewPitch - pitchBob) * 0.1F));
        poses.mulPose(Axis.YP.rotationDegrees((viewYaw - yawBob) * 0.1F));

        final Matrix4f expected = new Matrix4f()
                .rotateX((viewPitch - pitchBob) * 0.1F * (float) Math.PI / 180.0F)
                .rotateY((viewYaw - yawBob) * 0.1F * (float) Math.PI / 180.0F);
        assertMatrixEquals(expected, poses.last().pose());
    }

    @Test
    void pitchMovesVisibleArmAndItemAnchorThroughTheSameSemanticParentChain() {
        final List<BedrockPlayerModelMetadata.Bone> armChain = chain("rightarm");
        final List<BedrockPlayerModelMetadata.Bone> itemChain = chain("rightarm", "rightitem");
        final Map<String, Matrix4f> flat = locals(0.0F);
        final Map<String, Matrix4f> pitched = locals(60.0F);
        final Map<String, Matrix4f> bind = bindLocals();

        final Matrix4f flatArm = world(armChain, flat, bind);
        final Matrix4f pitchedArm = world(armChain, pitched, bind);
        final Matrix4f flatItem = world(itemChain, flat, bind);
        final Matrix4f pitchedItem = world(itemChain, pitched, bind);

        final Vector3f itemPivot = BedrockTransformConvention.toJavaModel(
                new Vector3f(-6.0F, 15.0F, 1.0F));
        final Vector3f flatArmPoint = flatArm.transformPosition(new Vector3f(itemPivot).div(16.0F));
        final Vector3f pitchedArmPoint = pitchedArm.transformPosition(new Vector3f(itemPivot).div(16.0F));
        final Vector3f flatItemPoint = flatItem.transformPosition(new Vector3f(itemPivot).div(16.0F));
        final Vector3f pitchedItemPoint = pitchedItem.transformPosition(new Vector3f(itemPivot).div(16.0F));
        assertTrue(flatArmPoint.distance(pitchedArmPoint) > 0.05F,
                () -> "arm pitch displacement=" + flatArmPoint.distance(pitchedArmPoint));
        assertTrue(flatItemPoint.distance(pitchedItemPoint) > 0.05F,
                () -> "item pitch displacement=" + flatItemPoint.distance(pitchedItemPoint));

        final Vector3f flatArmDirection = flatArm.transformDirection(new Vector3f(0.0F, 0.0F, 1.0F)).normalize();
        final Vector3f pitchedArmDirection = pitchedArm.transformDirection(
                new Vector3f(0.0F, 0.0F, 1.0F)).normalize();
        assertTrue(flatArmDirection.distance(pitchedArmDirection) > 0.25F,
                () -> "body pitch did not rotate the visible arm direction: "
                        + flatArmDirection + " -> " + pitchedArmDirection);

        assertMatrixEquals(new Matrix4f(flatArm).invert().mul(flatItem),
                new Matrix4f(pitchedArm).invert().mul(pitchedItem));

        // The flattened PlayerModel arm render consumes its absolute current ModelPart transform;
        // the prefix supplies the semantic parent chain exactly once.
        final Matrix4f armAbsolute = pitched.get("rightarm");
        final Matrix4f bodyWorld = world(chain(), pitched, bind);
        final Matrix4f finalVisibleArm = new Matrix4f(bodyWorld).mul(armAbsolute);
        assertMatrixEquals(pitchedArm, finalVisibleArm);
    }

    @Test
    void flattenedArmPrefixMustRemoveNonIdentityBodyBindPoseBeforeApplyingArmAbsolutePose() {
        final Map<String, Matrix4f> current = locals(60.0F);
        final Map<String, Matrix4f> bind = bindLocals();
        bind.put("body", BedrockTransformConvention.deformation(
                BedrockTransformConvention.toJavaModel(new Vector3f(0.0F, 24.0F, 0.0F)),
                new Vector3f(), new Vector3f(12.0F, -8.0F, 4.0F), new Vector3f(1.0F)));

        final Matrix4f expected = world(chain("rightarm"), current, bind);
        final Matrix4f parentWorld = world(chain(), current, bind);
        final Matrix4f currentArmAbsolute = current.get("rightarm");
        final Matrix4f actual = new Matrix4f(parentWorld)
                .mul(new Matrix4f(bind.get("body")).invert())
                .mul(currentArmAbsolute);

        assertMatrixEquals(expected, actual);
    }

    @Test
    void firstPersonFinalMatricesKeepOnlyCameraRebound() {
        final Matrix4f neutralArm = finalCameraMatrix(0.0F, 0.0F, 0.0F, 0.0F, false);
        final Matrix4f neutralItem = finalCameraMatrix(0.0F, 0.0F, 0.0F, 0.0F, true);

        // Absolute camera look is already owned by Java's hand pass. Once bob catches up, it must
        // not reappear through the Bedrock body queries.
        assertMatrixEquals(neutralArm,
                finalCameraMatrix(80.0F, 80.0F, -120.0F, -120.0F, false));
        assertMatrixEquals(neutralItem,
                finalCameraMatrix(80.0F, 80.0F, -120.0F, -120.0F, true));

        final Matrix4f reboundArm = finalCameraMatrix(80.0F, 69.0F, -120.0F, -113.0F, false);
        final Matrix4f reboundItem = finalCameraMatrix(80.0F, 69.0F, -120.0F, -113.0F, true);
        assertMatrixChanged(neutralArm, reboundArm);
        assertMatrixChanged(neutralItem, reboundItem);
        assertMatrixEquals(new Matrix4f(neutralArm).invert().mul(neutralItem),
                new Matrix4f(reboundArm).invert().mul(reboundItem));
    }

    private static Matrix4f finalCameraMatrix(float viewPitch, float pitchBob,
                                              float viewYaw, float yawBob,
                                              boolean itemAnchor) {
        final PoseStack poses = new PoseStack();
        poses.mulPose(Axis.XP.rotationDegrees((viewPitch - pitchBob) * 0.1F));
        poses.mulPose(Axis.YP.rotationDegrees((viewYaw - yawBob) * 0.1F));
        final Matrix4f host = world(
                itemAnchor ? chain("rightarm", "rightitem") : chain("rightarm"),
                locals(0.0F), bindLocals());
        poses.mulPose(host);
        return new Matrix4f(poses.last().pose());
    }

    private static Matrix4f world(List<BedrockPlayerModelMetadata.Bone> chain,
                                  Map<String, Matrix4f> current,
                                  Map<String, Matrix4f> bind) {
        return AttachableHostContext.firstPersonWorldMatrix(chain,
                bone -> new Matrix4f(current.get(bone.key())),
                bone -> new Matrix4f(bind.get(bone.key())));
    }

    private static Map<String, Matrix4f> bindLocals() {
        final Map<String, Matrix4f> result = new LinkedHashMap<>();
        result.put("root", new Matrix4f());
        result.put("waist", new Matrix4f());
        result.put("body", BedrockTransformConvention.deformation(
                BedrockTransformConvention.toJavaModel(new Vector3f(0.0F, 24.0F, 0.0F)),
                new Vector3f(), new Vector3f(), new Vector3f(1.0F)));
        result.put("rightarm", BedrockTransformConvention.deformation(
                BedrockTransformConvention.toJavaModel(new Vector3f(-5.0F, 22.0F, 0.0F)),
                new Vector3f(), new Vector3f(), new Vector3f(1.0F)));
        result.put("rightitem", new Matrix4f());
        return result;
    }

    private static Map<String, Matrix4f> locals(float pitchDegrees) {
        final Map<String, Matrix4f> result = new LinkedHashMap<>();
        result.put("root", new Matrix4f());
        result.put("waist", new Matrix4f());
        result.put("body", BedrockTransformConvention.deformation(
                BedrockTransformConvention.toJavaModel(new Vector3f(0.0F, 24.0F, 0.0F)),
                new Vector3f(), new Vector3f(pitchDegrees, 0.0F, 0.0F), new Vector3f(1.0F)));
        result.put("rightarm", BedrockTransformConvention.deformation(
                BedrockTransformConvention.toJavaModel(new Vector3f(-5.0F, 22.0F, 0.0F)),
                new Vector3f(13.5F, 10.0F, 12.0F),
                new Vector3f(95.0F, -45.0F, 115.0F), new Vector3f(1.0F)));
        result.put("rightitem", new Matrix4f());
        return result;
    }

    private static List<BedrockPlayerModelMetadata.Bone> chain(String... descendants) {
        final List<BedrockPlayerModelMetadata.Bone> result = new ArrayList<>();
        result.add(bone("root", ""));
        result.add(bone("waist", "root"));
        result.add(bone("body", "waist"));
        String parent = "body";
        for (String descendant : descendants) {
            result.add(bone(descendant, parent));
            parent = descendant;
        }
        return result;
    }

    private static BedrockPlayerModelMetadata.Bone bone(String key, String parent) {
        return new BedrockPlayerModelMetadata.Bone(
                key, key, parent, parent, new Vector3f(), new Vector3f(),
                null, Map.of(), null);
    }

    private static void assertMatrixEquals(Matrix4f expected, Matrix4f actual) {
        final float[] expectedValues = expected.get(new float[16]);
        final float[] actualValues = actual.get(new float[16]);
        for (int index = 0; index < expectedValues.length; index++) {
            assertEquals(expectedValues[index], actualValues[index], 1.0e-5F,
                    "matrix element " + index);
        }
    }

    private static void assertMatrixChanged(Matrix4f before, Matrix4f after) {
        assertTrue(matrixDistance(before, after) > 1.0e-5F,
                () -> "expected final matrix to change: " + matrixDistance(before, after));
    }

    private static float matrixDistance(Matrix4f before, Matrix4f after) {
        final Vector3f probe = new Vector3f(0.25F, 0.5F, 0.75F);
        final Vector3f beforePoint = new Matrix4f(before).transformPosition(new Vector3f(probe));
        final Vector3f afterPoint = new Matrix4f(after).transformPosition(new Vector3f(probe));
        return beforePoint.distance(afterPoint);
    }

}
