package org.oryxel.viabedrockutility.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeometryUtilHierarchyTest {
    @Test
    void playerPresentationFlatteningDoesNotDefineTheSemanticParent() {
        assertEquals("root", GeometryUtil.presentationParent(true, "rightArm", "body"));
        assertEquals("root", GeometryUtil.presentationParent(true, "body", "waist"));
        assertEquals("rightArm", GeometryUtil.presentationParent(true, "rightItem", "rightArm"));
        assertEquals("body", GeometryUtil.presentationParent(false, "rightArm", "body"));
    }
}
