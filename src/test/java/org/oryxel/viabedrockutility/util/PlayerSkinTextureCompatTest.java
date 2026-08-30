package org.oryxel.viabedrockutility.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSkinTextureCompatTest {
    @Test
    void mirrorsLegacyRightLimbsIntoTransparentLeftLimbRegions() {
        final byte[] skin = new byte[64 * 64 * 4];
        paintSourceRegion(skin, 0, 16, 16, 16);
        paintSourceRegion(skin, 40, 16, 16, 16);

        final PlayerSkinTextureCompat.RepairResult result =
                PlayerSkinTextureCompat.repairMissingLeftLimbs(skin, 64, 64);

        assertTrue(result.leftArm());
        assertTrue(result.leftLeg());
        assertMirroredRect(skin, 4, 16, 20, 48, 4, 4);
        assertMirroredRect(skin, 8, 16, 24, 48, 4, 4);
        assertMirroredRect(skin, 0, 20, 24, 52, 4, 12);
        assertMirroredRect(skin, 4, 20, 20, 52, 4, 12);
        assertMirroredRect(skin, 8, 20, 16, 52, 4, 12);
        assertMirroredRect(skin, 12, 20, 28, 52, 4, 12);
        assertMirroredRect(skin, 44, 16, 36, 48, 4, 4);
        assertMirroredRect(skin, 48, 16, 40, 48, 4, 4);
        assertMirroredRect(skin, 40, 20, 40, 52, 4, 12);
        assertMirroredRect(skin, 44, 20, 36, 52, 4, 12);
        assertMirroredRect(skin, 48, 20, 32, 52, 4, 12);
        assertMirroredRect(skin, 52, 20, 44, 52, 4, 12);
    }

    @Test
    void preservesAuthoredLeftLimbs() {
        final byte[] skin = new byte[64 * 64 * 4];
        paintSourceRegion(skin, 0, 16, 16, 16);
        paintSourceRegion(skin, 40, 16, 16, 16);
        setPixel(skin, 16, 48, 1, 2, 3, 4);
        setPixel(skin, 32, 48, 5, 6, 7, 8);
        final byte[] before = skin.clone();

        final PlayerSkinTextureCompat.RepairResult result =
                PlayerSkinTextureCompat.repairMissingLeftLimbs(skin, 64, 64);

        assertFalse(result.repairedAny());
        assertArrayEquals(before, skin);
    }

    private static void paintSourceRegion(byte[] skin, int x, int y, int width, int height) {
        for (int row = y; row < y + height; row++) {
            for (int column = x; column < x + width; column++) {
                setPixel(skin, column, row, column, row, column ^ row, 255);
            }
        }
    }

    private static void assertMirroredRect(byte[] skin, int sourceX, int sourceY,
                                           int targetX, int targetY, int width, int height) {
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                assertArrayEquals(
                        pixel(skin, sourceX + column, sourceY + row),
                        pixel(skin, targetX + width - 1 - column, targetY + row)
                );
            }
        }
    }

    private static byte[] pixel(byte[] skin, int x, int y) {
        final int offset = (y * 64 + x) * 4;
        return new byte[]{skin[offset], skin[offset + 1], skin[offset + 2], skin[offset + 3]};
    }

    private static void setPixel(byte[] skin, int x, int y, int r, int g, int b, int a) {
        final int offset = (y * 64 + x) * 4;
        skin[offset] = (byte) r;
        skin[offset + 1] = (byte) g;
        skin[offset + 2] = (byte) b;
        skin[offset + 3] = (byte) a;
    }
}
