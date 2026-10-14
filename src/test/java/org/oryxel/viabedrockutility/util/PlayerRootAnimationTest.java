package org.oryxel.viabedrockutility.util;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.oryxel.viabedrockutility.adapter.McBoneModel;

import static org.junit.jupiter.api.Assertions.*;

class PlayerRootAnimationTest {
    @Test
    void convertedStandardSkinAcceptsWholeBodyRotationAtItsSyntheticRoot() {
        var geometry = BundledPlayerModelFactory.geometry(false);
        geometry.getParents().removeIf(bone -> bone.getName().equals("root"));
        assertTrue(geometry.getParents().stream().noneMatch(bone -> bone.getName().equals("root")));
        var model = GeometryUtil.buildModel(geometry, true, false);
        var bones = new McBoneModel(model).getBoneIndex();
        var root = bones.get("root");
        assertNotNull(root, "Whole-body animations must resolve the skin's root");
        root.addRotation(new Vector3f(-65, 0, 0));
        assertEquals(-65, root.getRotation().x, 0.001f);
        assertTrue(root.getChildren().containsKey("body"));
        root.resetToDefaultPose();
        assertEquals(0, root.getRotation().x, 0.001f);
    }

}
