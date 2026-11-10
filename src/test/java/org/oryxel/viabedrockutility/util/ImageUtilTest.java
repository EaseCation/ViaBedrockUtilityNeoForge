package org.oryxel.viabedrockutility.util;

import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ImageUtilTest {
    @Test void directPixelsMatchPreviousPngPathForSkinsCapesAndAnimationSheets() throws Exception {
        for (int[] size : new int[][]{{1, 1}, {64, 32}, {64, 64}, {128, 128}, {32, 256}}) {
            int width = size[0], height = size[1];
            byte[] rgba = new byte[width * height * 4];
            new Random(37).nextBytes(rgba);
            // Preserve color even when alpha is zero; cover signed channels and intermediate alpha.
            for (int i = 3; i < rgba.length; i += 4) rgba[i] = (byte) ((i / 4) % 256);
            byte[] original = rgba.clone();
            var encoded = new ByteArrayOutputStream();
            assertTrue(ImageIO.write(ImageUtil.toBufferedImage(rgba, width, height), "png", encoded));
            try (var old = NativeImage.read(encoded.toByteArray());
                 var converted = ImageUtil.toNativeImage(rgba, width, height)) {
                assertNotNull(converted);
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    assertEquals(old.getPixel(x, y), converted.getPixel(x, y), "RGBA at " + x + "," + y);
                }
            }
            assertArrayEquals(original, rgba);
        }
    }

    @Test void rejectsInconsistentDimensionsBeforeAllocatingNativeMemory() {
        assertNull(ImageUtil.toNativeImage(null, 1, 1));
        assertNull(ImageUtil.toNativeImage(new byte[4], 0, 1));
        assertNull(ImageUtil.toNativeImage(new byte[4], 1, -1));
        assertNull(ImageUtil.toNativeImage(new byte[3], 1, 1));
        assertNull(ImageUtil.toNativeImage(new byte[5], 1, 1));
        assertNull(ImageUtil.toNativeImage(new byte[4], Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
    @Test void largeImageConversionIsBoundedAndCancelledImagesCannotBePublished() {
        byte[] rgba = new byte[128 * 256 * 4];
        new Random(7).nextBytes(rgba);
        try (var conversion = ImageUtil.beginConversion(rgba, 128, 256)) {
            assertFalse(conversion.step(16_384));
            assertThrows(IllegalStateException.class, conversion::take);
            assertTrue(conversion.step(16_384));
            try (var image = conversion.take(); var expected = ImageUtil.toNativeImage(rgba, 128, 256)) {
                assertEquals(expected.getPixel(127, 255), image.getPixel(127, 255));
                conversion.close(); // Ownership was transferred; pixels remain usable.
                assertEquals(expected.getPixel(0, 0), image.getPixel(0, 0));
            }
        }
        var cancelled = ImageUtil.beginConversion(rgba, 128, 256);
        cancelled.step(1); cancelled.close(); cancelled.close();
        assertThrows(IllegalStateException.class, cancelled::take);
        assertThrows(IllegalStateException.class, () -> cancelled.step(1));
    }

}
