package org.oryxel.viabedrockutility.pack.processor;

import net.easecation.bedrockmotion.pack.content.Content;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TextureProcessor {
    private static final Map<ResourceLocation, LazyBedrockTexture> REGISTERED_TEXTURES =
            new ConcurrentHashMap<>();

    public static void process(final List<Content> packs) {
        final Minecraft client = Minecraft.getInstance();
        final Map<ResourceLocation, LazyBedrockTexture> nextGeneration = new LinkedHashMap<>();

        int registered = 0;
        int preloaded = 0;
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

                LazyBedrockTexture texture = null;
                try {
                    final ResourceLocation identifier = normalizeTextureIdentifier(path);
                    final LazyBedrockTexture previous = REGISTERED_TEXTURES.get(identifier);
                    texture = new LazyBedrockTexture(
                            () -> identifier.toString(), image.getPngBytes(), failure ->
                            ViaBedrockUtilityNeoForge.LOGGER.warn(
                                    "[ResourcePack:Texture] Unable to decode texture {}", path, failure));
                    if (previous != null && previous.isLoaded()) {
                        texture.preload();
                        preloaded++;
                    }
                    client.getTextureManager().register(identifier, texture);
                    nextGeneration.put(identifier, texture);
                    registered++;
                } catch (final RuntimeException e) {
                    if (texture != null) {
                        texture.close();
                    }
                    // RuntimeException covers ResourceLocationException for paths with characters
                    // outside [a-z0-9/._-]; skip the single bad texture instead of aborting the whole
                    // registration pass (which runs on the render thread and would crash the client).
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
                "[ResourcePack:Texture] Registered {} lazy textures ({} active texture(s) refreshed, "
                        + "{} metadata sidecars handled by ResourceManager, {} failed)",
                registered, preloaded, metadata, failed
        );

        REGISTERED_TEXTURES.keySet().stream()
                .filter(identifier -> !nextGeneration.containsKey(identifier))
                .forEach(client.getTextureManager()::release);
        REGISTERED_TEXTURES.clear();
        REGISTERED_TEXTURES.putAll(nextGeneration);
    }

    /** Returns the exact resource location used for a Bedrock texture path. */
    public static ResourceLocation normalizeTextureIdentifier(final String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("Texture path is blank");
        }

        final String normalized = rawPath.trim().toLowerCase(Locale.ROOT)
                .replace(".png", "").replace(".jpg", "");
        return normalized.indexOf(':') >= 0
                ? ResourceLocation.parse(normalized)
                : ResourceLocation.withDefaultNamespace(normalized);
    }

    /** Used by the particle loader to diagnose an unresolved Bedrock texture before rendering. */
    public static boolean isRegisteredTexture(final String rawPath) {
        try {
            return REGISTERED_TEXTURES.containsKey(normalizeTextureIdentifier(rawPath));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** Releases every dynamic texture owned by the retired server-pack generation. */
    public static void clear() {
        final Minecraft client = Minecraft.getInstance();
        REGISTERED_TEXTURES.keySet().forEach(client.getTextureManager()::release);
        REGISTERED_TEXTURES.clear();
    }
}
