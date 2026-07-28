package org.oryxel.viabedrockutility.util;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockCubeFaceMappingTest {
    @Test
    void northFaceWithNegativeUvSizeDoesNotMoveToSouth() {
        Map<org.cube.converter.util.element.Direction, Float[]> source = new EnumMap<>(
                org.cube.converter.util.element.Direction.class);
        source.put(org.cube.converter.util.element.Direction.NORTH, new Float[]{1F, 64F, 0F, 0F});

        Map<Direction, Float[]> mapped = BedrockCubeFaceMapping.toJavaFaces(source);

        assertTrue(mapped.containsKey(Direction.NORTH));
        assertFalse(mapped.containsKey(Direction.SOUTH));
        assertArrayEquals(new Float[]{1F, 64F, 0F, 0F}, mapped.get(Direction.NORTH));
    }

    @Test
    void coordinateReflectionsUseFixedFaceMapping() {
        Map<org.cube.converter.util.element.Direction, Float[]> source = new EnumMap<>(
                org.cube.converter.util.element.Direction.class);
        source.put(org.cube.converter.util.element.Direction.NORTH, new Float[]{10F, 0F, 11F, 1F});
        source.put(org.cube.converter.util.element.Direction.SOUTH, new Float[]{20F, 0F, 21F, 1F});
        source.put(org.cube.converter.util.element.Direction.EAST, new Float[]{30F, 0F, 31F, 1F});
        source.put(org.cube.converter.util.element.Direction.WEST, new Float[]{40F, 0F, 41F, 1F});
        source.put(org.cube.converter.util.element.Direction.UP, new Float[]{50F, 0F, 51F, 1F});
        source.put(org.cube.converter.util.element.Direction.DOWN, new Float[]{60F, 0F, 61F, 1F});

        Map<Direction, Float[]> mapped = BedrockCubeFaceMapping.toJavaFaces(source);

        assertArrayEquals(new Float[]{10F, 0F, 11F, 1F}, mapped.get(Direction.NORTH));
        assertArrayEquals(new Float[]{20F, 0F, 21F, 1F}, mapped.get(Direction.SOUTH));
        assertArrayEquals(new Float[]{30F, 0F, 31F, 1F}, mapped.get(Direction.WEST));
        assertArrayEquals(new Float[]{40F, 0F, 41F, 1F}, mapped.get(Direction.EAST));
        assertArrayEquals(new Float[]{50F, 0F, 51F, 1F}, mapped.get(Direction.DOWN));
        assertArrayEquals(new Float[]{60F, 0F, 61F, 1F}, mapped.get(Direction.UP));
    }
}
