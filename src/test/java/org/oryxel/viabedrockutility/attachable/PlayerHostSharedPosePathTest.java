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
    void bedrockCameraSpaceKeepsPitchButRemovesVanillaYaw() {
        final float viewPitch = 67.0F;
        final float pitchBob = 4.0F;
        final float viewYaw = -123.0F;
        final float yawBob = 7.0F;
        final PoseStack poses = new PoseStack();
        poses.mulPose(Axis.XP.rotationDegrees((viewPitch - pitchBob) * 0.1F));
        poses.mulPose(Axis.YP.rotationDegrees((viewYaw - yawBob) * 0.1F));

        FirstPersonAttachableRenderer.removeVanillaHandYawTransform(
                poses, viewYaw, yawBob);

        final Matrix4f expected = new Matrix4f()
                .rotateX((float) Math.toRadians((viewPitch - pitchBob) * 0.1F));
        assertMatrixEquals(expected, poses.last().pose());
    }

    @Test
    void pitchMovesVisibleArmAndItemAnchorThroughTheSameSemanticParentChain() {
        final List<BedrockPlayerModelMetadata.Bone> armChain = chain("rightarm");
        final List<BedrockPlayerModelMetadata.Bone> itemChain = chain("rightarm", "rightitem");
        final Map<String, Matrix4f> flat = locals(0.0F);
        final Map<String, Matrix4f> pitched = locals(60.0F);

        final Matrix4f flatArm = world(armChain, flat);
        final Matrix4f pitchedArm = world(armChain, pitched);
        final Matrix4f flatItem = world(itemChain, flat);
        final Matrix4f pitchedItem = world(itemChain, pitched);

        final Vector3f flatArmPoint = flatArm.transformPosition(new Vector3f());
        final Vector3f pitchedArmPoint = pitchedArm.transformPosition(new Vector3f());
        final Vector3f flatItemPoint = flatItem.transformPosition(new Vector3f());
        final Vector3f pitchedItemPoint = pitchedItem.transformPosition(new Vector3f());
        assertTrue(Math.abs(flatArmPoint.y - pitchedArmPoint.y) > 0.25F);
        assertTrue(Math.abs(flatItemPoint.y - pitchedItemPoint.y) > 0.25F);

        assertMatrixEquals(new Matrix4f(flatArm).invert().mul(flatItem),
                new Matrix4f(pitchedArm).invert().mul(pitchedItem));

        final Matrix4f armLocal = pitched.get("rightarm");
        final Matrix4f finalVisibleArm = AttachableHostContext.armRenderPrefix(
                pitchedArm, armLocal).mul(armLocal);
        assertMatrixEquals(pitchedArm, finalVisibleArm);
    }

    private static Matrix4f world(List<BedrockPlayerModelMetadata.Bone> chain,
                                  Map<String, Matrix4f> locals) {
        return AttachableHostContext.firstPersonWorldMatrix(chain,
                bone -> new Matrix4f(locals.get(bone.key())));
    }

    private static Map<String, Matrix4f> locals(float pitchDegrees) {
        final Map<String, Matrix4f> result = new LinkedHashMap<>();
        result.put("root", new Matrix4f());
        result.put("waist", new Matrix4f());
        result.put("body", new Matrix4f().rotateX((float) Math.toRadians(pitchDegrees)));
        result.put("rightarm", new Matrix4f().translate(0.75F, 1.0F, 1.0F));
        result.put("rightitem", new Matrix4f().translate(0.0F, -0.5F, 0.75F));
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
}
