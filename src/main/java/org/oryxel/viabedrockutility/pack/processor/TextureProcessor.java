package org.oryxel.viabedrockutility.pack.processor;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.oryxel.viabedrockutility.fabric.ViaBedrockUtilityFabric;
import net.easecation.bedrockmotion.pack.content.Content;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class TextureProcessor {
    public static void process(final List<Content> packs) {
        final Minecraft client = Minecraft.getInstance();

        for (final Content content : packs) {
            for (final String path : content.getFilesDeep("textures/", "")) {
                final Content.LazyImage image = content.getShortnameImage(path);
                if (image == null) {
                    continue;
                }

                try {
                    final ResourceLocation identifier = ResourceLocation.withDefaultNamespace(path.toLowerCase(Locale.ROOT).replace(".png", "").replace(".jpg", ""));
                    final NativeImage image1 = NativeImage.read(image.getPngBytes());
                    client.getTextureManager().register(identifier, new DynamicTexture(() -> identifier.toString() + image1.hashCode(), image1));
                } catch (final IOException e) {
                    ViaBedrockUtilityFabric.LOGGER.warn("Unable to register texture {}", path);
                }
            }
        }
    }
}
