package org.oryxel.viabedrockutility.util;

import org.cube.converter.util.element.Direction;
import org.cube.converter.util.element.UVMap;

import java.util.Map;

final class CoincidentFaceUv {
    private static final float EPSILON = 1.0E-5F;

    private CoincidentFaceUv() {
    }

    static Float[] northUv(UVMap map, float sizeZ, float inflate, boolean mirror,
                           float textureWidth, float textureHeight) {
        Float[] north = map.getUvMap().get(Direction.NORTH);
        if (!mirror && hasRepeatedFaces(map.getUvType(), map.getUvMap(), sizeZ, inflate,
                textureWidth, textureHeight)) {
            return flipU(north);
        }
        return north;
    }

    static Float[] southUv(UVMap map, float sizeZ, float inflate, boolean mirror,
                           float textureWidth, float textureHeight) {
        return southUv(map.getUvType(), map.getUvMap(), sizeZ, inflate, mirror,
                textureWidth, textureHeight);
    }

    static Float[] northUv(UVMap.UVType uvType, Map<Direction, Float[]> faces,
                           float sizeZ, float inflate, boolean mirror,
                           float textureWidth, float textureHeight) {
        Float[] north = faces.get(Direction.NORTH);
        return !mirror && hasRepeatedFaces(uvType, faces, sizeZ, inflate, textureWidth, textureHeight)
                ? flipU(north)
                : north;
    }

    static Float[] southUv(UVMap.UVType uvType, Map<Direction, Float[]> faces,
                           float sizeZ, float inflate, boolean mirror,
                           float textureWidth, float textureHeight) {
        Float[] south = faces.get(Direction.SOUTH);
        if (!hasRepeatedFaces(uvType, faces, sizeZ, inflate, textureWidth, textureHeight)) {
            return south;
        }

        Float[] north = faces.get(Direction.NORTH);
        // NORTH and SOUTH use opposite vertex winding. Flip U so their repeated texture
        // samples occupy the same pixels. The cube mirror then selects the outward direction.
        return mirror ? flipU(north) : north;
    }

    private static boolean hasRepeatedFaces(UVMap.UVType uvType, Map<Direction, Float[]> faces,
                                            float sizeZ, float inflate,
                                            float textureWidth, float textureHeight) {
        return uvType == UVMap.UVType.BOX
                && Math.abs(sizeZ) <= EPSILON
                && Math.abs(inflate) <= EPSILON
                && repeatEquivalent(faces.get(Direction.NORTH), faces.get(Direction.SOUTH),
                textureWidth, textureHeight);
    }

    private static Float[] flipU(Float[] uv) {
        return new Float[]{uv[2], uv[1], uv[0], uv[3]};
    }

    private static boolean repeatEquivalent(Float[] first, Float[] second,
                                             float textureWidth, float textureHeight) {
        if (first == null || second == null || textureWidth <= 0F || textureHeight <= 0F) {
            return false;
        }

        float deltaU = second[0] - first[0];
        float deltaV = second[1] - first[1];
        return close(second[2] - first[2], deltaU)
                && close(second[3] - first[3], deltaV)
                && wholeTextureOffset(deltaU, textureWidth)
                && wholeTextureOffset(deltaV, textureHeight);
    }

    private static boolean wholeTextureOffset(float delta, float textureSize) {
        return close(delta / textureSize, Math.round(delta / textureSize));
    }

    private static boolean close(float first, float second) {
        return Math.abs(first - second) <= EPSILON;
    }
}
