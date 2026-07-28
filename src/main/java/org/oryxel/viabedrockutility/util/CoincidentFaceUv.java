package org.oryxel.viabedrockutility.util;

import org.cube.converter.util.element.Direction;
import org.cube.converter.util.element.UVMap;

import java.util.Map;

final class CoincidentFaceUv {
    private static final float EPSILON = 1.0E-5F;

    private CoincidentFaceUv() {
    }

    static Float[] southUv(UVMap map, float sizeZ, float inflate, float textureWidth, float textureHeight) {
        return southUv(map.getUvType(), map.getUvMap(), sizeZ, inflate, textureWidth, textureHeight);
    }

    static Float[] southUv(UVMap.UVType uvType, Map<Direction, Float[]> faces,
                           float sizeZ, float inflate, float textureWidth, float textureHeight) {
        Float[] south = faces.get(Direction.SOUTH);
        if (uvType != UVMap.UVType.BOX || Math.abs(sizeZ) > EPSILON || Math.abs(inflate) > EPSILON) {
            return south;
        }

        Float[] north = faces.get(Direction.NORTH);
        if (!repeatEquivalent(north, south, textureWidth, textureHeight)) {
            return south;
        }

        // NORTH and SOUTH use opposite vertex winding. Flip U so their repeated texture
        // samples occupy the same pixels instead of exposing a mirrored alpha silhouette.
        return new Float[]{north[2], north[1], north[0], north[3]};
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
