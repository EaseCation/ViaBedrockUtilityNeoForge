package org.oryxel.viabedrockutility.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BundledPlayerModelFactoryTest {
    private static final float EPSILON = 1.0E-4F;

    @Test
    void wideGeometryCarriesStandardBedrockItemMetadata() {
        final var geometry = BundledPlayerModelFactory.geometry(false);

        assertEquals("geometry.humanoid.custom", geometry.getIdentifier());
        assertEquals(22.0F, bone(geometry, "rightArm").getPivot().getY(), EPSILON);
        assertEquals(15.0F, bone(geometry, "rightItem").getPivot().getY(), EPSILON);
        assertEquals(1.0F, bone(geometry, "rightItem").getPivot().getZ(), EPSILON);
    }

    @Test
    void slimGeometryCarriesVanillaSlimArmAndItemPivots() {
        final var geometry = BundledPlayerModelFactory.geometry(true);

        assertEquals("geometry.humanoid.customSlim", geometry.getIdentifier());
        assertEquals(21.5F, bone(geometry, "rightArm").getPivot().getY(), EPSILON);
        assertEquals(14.5F, bone(geometry, "rightItem").getPivot().getY(), EPSILON);
        assertEquals(21.5F, bone(geometry, "leftArm").getPivot().getY(), EPSILON);
        assertEquals(14.5F, bone(geometry, "leftItem").getPivot().getY(), EPSILON);
    }

    private static org.cube.converter.model.element.Parent bone(
            org.cube.converter.model.impl.bedrock.BedrockGeometryModel geometry, String name) {
        final var bone = geometry.getParents().stream()
                .filter(candidate -> name.equals(candidate.getName()))
                .findFirst().orElse(null);
        assertNotNull(bone);
        return bone;
    }
}
