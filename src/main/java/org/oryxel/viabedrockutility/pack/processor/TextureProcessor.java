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

        int registered = 0;
        int metadata = 0;
        int failed = 0;

        for (final Content content : packs) {
            for (final String path : content.getFilesDeep("textures/", "")) {
                final Content.LazyImage image = content.getShortnameImage(path);
                if (image == null) {
                    // Non-image entries (e.g. nine-slice .json sidecars) are handled by the
                    // ViaBedrock-converted Java pack via the ResourceManager, not here.
                    if (path.endsWith(".json")) {
                        metadata++;
                    }
                    continue;
                }

                try {
                    final ResourceLocation identifier = ResourceLocation.withDefaultNamespace(path.toLowerCase(Locale.ROOT).replace(".png", "").replace(".jpg", ""));
                    final NativeImage image1 = NativeImage.read(image.getPngBytes());
                    client.getTextureManager().register(identifier, new DynamicTexture(() -> identifier.toString() + image1.hashCode(), image1));
                    registered++;
                } catch (final IOException e) {
                    failed++;
                    ViaBedrockUtilityNeoForge.LOGGER.warn("[ResourcePack:Texture] Unable to register texture {}", path, e);
                }
            }
        }

        // IMPORTANT: this runs on the Render thread (DownloadedPackSource reload). Per-texture
        // INFO logging here previously emitted ~13.5k synchronous console writes through
        // TerminalConsoleAppender, blocking the render thread for minutes and causing the
        // Bedrock connection to time out. Emit a single summary line instead. Use debug for
        // per-texture detail if you ever need to re-enable it.
        ViaBedrockUtilityNeoForge.LOGGER.info(
                "[ResourcePack:Texture] Registered {} textures ({} metadata sidecars handled by ResourceManager, {} failed)",
                registered, metadata, failed
        );
    }
}
