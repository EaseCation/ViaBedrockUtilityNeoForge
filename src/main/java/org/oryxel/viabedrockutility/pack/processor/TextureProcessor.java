package org.oryxel.viabedrockutility.pack.processor;

import com.mojang.blaze3d.platform.NativeImage;
import net.easecation.bedrockmotion.pack.content.Content;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;

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
                    if (path.endsWith(".json")) {
                        ViaBedrockUtilityNeoForge.LOGGER.info("[ResourcePack:Texture] Found texture metadata path={}", path);
                    } else {
                        ViaBedrockUtilityNeoForge.LOGGER.debug("[ResourcePack:Texture] Skipping non-image texture entry: {}", path);
                    }
                    continue;
                }

                try {
                    final ResourceLocation identifier = ResourceLocation.withDefaultNamespace(path.toLowerCase(Locale.ROOT).replace(".png", "").replace(".jpg", ""));
                    final NativeImage image1 = NativeImage.read(image.getPngBytes());
                    client.getTextureManager().register(identifier, new DynamicTexture(() -> identifier.toString() + image1.hashCode(), image1));
                    ViaBedrockUtilityNeoForge.LOGGER.info(
                            "[ResourcePack:Texture] Registered texture path={} id={} size={}x{}",
                            path, identifier, image1.getWidth(), image1.getHeight()
                    );
                } catch (final IOException e) {
                    ViaBedrockUtilityNeoForge.LOGGER.warn("[ResourcePack:Texture] Unable to register texture {}", path, e);
                }
            }
        }
    }
}
