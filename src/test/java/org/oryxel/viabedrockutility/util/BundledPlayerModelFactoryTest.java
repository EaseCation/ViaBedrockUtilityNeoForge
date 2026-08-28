package org.oryxel.viabedrockutility.util;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void bundledVisualPackContainsNoPlayerHostDefinitions() throws Exception {
        final InputStream resource = BundledPlayerModelFactory.class.getResourceAsStream(
                "/assets/viabedrockutility/vanilla_packs/vanilla.mcpack");
        assertNotNull(resource);

        final Set<String> files = new LinkedHashSet<>();
        try (resource; ZipInputStream zip = new ZipInputStream(resource)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    files.add(entry.getName());
                }
            }
        }

        assertTrue(files.contains("models/entity/humanoid.custom.geo.json"));
        assertTrue(files.contains("textures/particle/particles.png"));
        assertFalse(files.isEmpty());
        assertTrue(files.stream().allMatch(path -> path.equals("LICENSE")
                || path.equals("manifest.json")
                || path.equals("models/entity/humanoid.custom.geo.json")
                || path.startsWith("textures/")), files.toString());
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
