package org.oryxel.viabedrockutility.util;

import com.mojang.blaze3d.platform.NativeImage;

import java.awt.image.BufferedImage;

public class ImageUtil {
    public static NativeImage toNativeImage(byte[] data, int width, int height) {
        if (!valid(data, width, height)) return null;
        try (var conversion = beginConversion(data, width, height)) {
            conversion.step(Integer.MAX_VALUE);
            return conversion.take();
        }
    }

    public static RgbaConversion beginConversion(byte[] data, int width, int height) {
        if (!valid(data, width, height)) throw new IllegalArgumentException("Invalid RGBA image");
        return new RgbaConversion(data, width, height);
    }

    private static boolean valid(byte[] data, int width, int height) {
        return data != null && width > 0 && height > 0 && (long) width * height <= Integer.MAX_VALUE / 4
                && data.length == (long) width * height * 4;
    }

    /** Unpublished pixels remain owned here until take(); cancellation closes native storage. */
    public static final class RgbaConversion implements AutoCloseable {
        private byte[] data;
        private NativeImage image;
        private final int width, pixels;
        private int next;
        private RgbaConversion(byte[] data, int width, int height) {
            this.data = data; this.width = width; this.pixels = width * height;
            image = new NativeImage(width, height, false);
        }
        public boolean step(int maximumPixels) {
            if (maximumPixels < 1 || image == null) throw new IllegalStateException("Inactive conversion");
            var profiler = net.minecraft.util.profiling.Profiler.get();
            profiler.push("ec:viabedrockutility/skin/image_convert");
            try {
                int end = next + Math.min(maximumPixels, pixels - next);
                while (next < end) {
                    image.setPixel(next % width, next / width, getARGB(next * 4, data));
                    next++;
                }
                return next == pixels;
            } catch (RuntimeException | Error failure) {
                close(); throw failure;
            } finally { profiler.pop(); }
        }
        public NativeImage take() {
            if (next != pixels || image == null) throw new IllegalStateException("Conversion is not complete");
            NativeImage result = image; image = null; data = null; return result;
        }
        @Override public void close() {
            if (image != null) { image.close(); image = null; }
            data = null;
        }
    }

    public static BufferedImage toBufferedImage(byte[] imageData, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, getARGB((y * width + x) * 4, imageData));
            }
        }
        return image;
    }

    private static int getARGB(int index, byte[] data) {
        return (data[index + 3] & 0xFF) << 24 | (data[index] & 0xFF) << 16 |
                (data[index + 1] & 0xFF) << 8 | (data[index + 2] & 0xFF);
    }
}
