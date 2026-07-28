package org.oryxel.viabedrockutility.util;

import org.cube.converter.util.element.Direction;
import org.cube.converter.util.element.UVMap;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CoincidentFaceUvTest {
    @Test
    void angelWingAlignsRepeatedSouthFaceToNorthProjection() {
        Map<Direction, Float[]> faces = wingFaces(false);

        assertArrayEquals(new Float[]{64F, 0F, 0F, 64F},
                CoincidentFaceUv.southUv(UVMap.UVType.BOX, faces, 0F, 0F, 64F, 64F));
    }

    @Test
    void mirroredAngelWingAlignsRepeatedSouthFaceToNorthProjection() {
        Map<Direction, Float[]> faces = wingFaces(true);

        assertArrayEquals(new Float[]{0F, 0F, 64F, 64F},
                CoincidentFaceUv.southUv(UVMap.UVType.BOX, faces, 0F, 0F, 64F, 64F));
    }

    @Test
    void distinctBackTextureIsPreserved() {
        Map<Direction, Float[]> faces = wingFaces(false);

        assertArrayEquals(new Float[]{64F, 0F, 128F, 64F},
                CoincidentFaceUv.southUv(UVMap.UVType.BOX, faces, 0F, 0F, 128F, 64F));
    }

    @Test
    void inflatedPlaneKeepsBothBoxUvFaces() {
        Map<Direction, Float[]> faces = wingFaces(false);

        assertArrayEquals(new Float[]{64F, 0F, 128F, 64F},
                CoincidentFaceUv.southUv(UVMap.UVType.BOX, faces, 0F, 0.25F, 64F, 64F));
    }

    private static Map<Direction, Float[]> wingFaces(boolean mirror) {
        Map<Direction, Float[]> faces = new EnumMap<>(Direction.class);
        faces.put(Direction.NORTH, mirror
                ? new Float[]{64F, 0F, 0F, 64F}
                : new Float[]{0F, 0F, 64F, 64F});
        faces.put(Direction.SOUTH, mirror
                ? new Float[]{128F, 0F, 64F, 64F}
                : new Float[]{64F, 0F, 128F, 64F});
        return faces;
    }
}
