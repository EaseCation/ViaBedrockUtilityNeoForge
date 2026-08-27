package org.oryxel.viabedrockutility.util;

/** Repairs 64x64 Bedrock skins that still leave the post-1.8 left-limb regions empty. */
public final class PlayerSkinTextureCompat {
    private static final int SKIN_SIZE = 64;
    private static final int CHANNELS = 4;

    private PlayerSkinTextureCompat() {
    }

    public static RepairResult repairMissingLeftLimbs(byte[] rgba, int width, int height) {
        if (width != SKIN_SIZE || height != SKIN_SIZE
                || rgba == null || rgba.length < width * height * CHANNELS) {
            return RepairResult.NONE;
        }

        final boolean repairLeg = isFullyTransparent(rgba, width, 16, 48, 16, 16);
        final boolean repairArm = isFullyTransparent(rgba, width, 32, 48, 16, 16);

        if (repairLeg) {
            copyRect(rgba, width, 4, 16, 20, 48, 4, 4, true);
            copyRect(rgba, width, 8, 16, 24, 48, 4, 4, true);
            copyRect(rgba, width, 0, 20, 24, 52, 4, 12, true);
            copyRect(rgba, width, 4, 20, 20, 52, 4, 12, true);
            copyRect(rgba, width, 8, 20, 16, 52, 4, 12, true);
            copyRect(rgba, width, 12, 20, 28, 52, 4, 12, true);
        }

        if (repairArm) {
            copyRect(rgba, width, 44, 16, 36, 48, 4, 4, true);
            copyRect(rgba, width, 48, 16, 40, 48, 4, 4, true);
            copyRect(rgba, width, 40, 20, 40, 52, 4, 12, true);
            copyRect(rgba, width, 44, 20, 36, 52, 4, 12, true);
            copyRect(rgba, width, 48, 20, 32, 52, 4, 12, true);
            copyRect(rgba, width, 52, 20, 44, 52, 4, 12, true);
        }

        return new RepairResult(repairArm, repairLeg);
    }

    private static boolean isFullyTransparent(byte[] rgba, int imageWidth,
                                              int x, int y, int width, int height) {
        for (int row = y; row < y + height; row++) {
            for (int column = x; column < x + width; column++) {
                if (rgba[pixelOffset(imageWidth, column, row) + 3] != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void copyRect(byte[] rgba, int imageWidth,
                                 int sourceX, int sourceY, int targetX, int targetY,
                                 int width, int height, boolean mirrorX) {
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                final int targetColumn = mirrorX ? width - 1 - column : column;
                System.arraycopy(
                        rgba, pixelOffset(imageWidth, sourceX + column, sourceY + row),
                        rgba, pixelOffset(imageWidth, targetX + targetColumn, targetY + row),
                        CHANNELS
                );
            }
        }
    }

    private static int pixelOffset(int imageWidth, int x, int y) {
        return (y * imageWidth + x) * CHANNELS;
    }

    public record RepairResult(boolean leftArm, boolean leftLeg) {
        public static final RepairResult NONE = new RepairResult(false, false);

        public boolean repairedAny() {
            return leftArm || leftLeg;
        }
    }
}
