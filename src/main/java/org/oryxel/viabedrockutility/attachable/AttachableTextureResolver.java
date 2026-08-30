package org.oryxel.viabedrockutility.attachable;

import net.easecation.bedrockmotion.pack.PackManager;
import net.easecation.bedrockmotion.pack.content.Content;
import org.oryxel.viabedrockutility.util.GeometryUtil;

import java.util.Locale;
import java.util.Map;

/** Resolves Bedrock texture aliases using the same top-most resource-pack precedence as definitions. */
final class AttachableTextureResolver {
    private AttachableTextureResolver() {
    }

    static GeometryUtil.TextureAlpha resolve(PackManager packs, Map<String, String> textureAliases,
                                              String alias, String selectedTexture) {
        if (packs == null || packs.getPacks() == null) {
            return null;
        }
        String path = textureAliases == null ? null : textureAliases.get(alias);
        if (path == null && "default".equalsIgnoreCase(alias)) {
            path = selectedTexture;
        }
        if (path == null || path.isBlank()) {
            path = alias;
        }
        final String normalized = normalize(path);
        for (int i = packs.getPacks().size() - 1; i >= 0; i--) {
            final Content content = packs.getPacks().get(i);
            try {
                final Content.LazyImage image = content.getShortnameImage(normalized);
                if (image != null && image.getImage() != null) {
                    return GeometryUtil.TextureAlpha.from(image.getImage());
                }
            } catch (RuntimeException ignored) {
                // A malformed optional texture must not suppress the attachable; the renderer
                // retains its rectangular front/back mesh when alpha data is unavailable.
            }
        }
        return null;
    }

    static String normalize(String raw) {
        String path = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        final int namespace = path.indexOf(':');
        if (namespace >= 0) {
            path = path.substring(namespace + 1);
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (!path.startsWith("textures/")) {
            path = "textures/" + path;
        }
        return path.replace(".png", "").replace(".jpg", "");
    }
}
