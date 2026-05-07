package org.oryxel.viabedrockutility.util;

import com.mojang.blaze3d.platform.NativeImage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;


import javax.imageio.ImageIO;

public class ImageUtil {
    public static NativeImage toNativeImage(byte[] data, int width, int height) {
        BufferedImage bufferedImage = toBufferedImage(data, width, height);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            ImageIO.write(bufferedImage, "png", outputStream);
            return NativeImage.read(outputStream.toByteArray());
        } catch (IOException ignored) {
            return null;
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
