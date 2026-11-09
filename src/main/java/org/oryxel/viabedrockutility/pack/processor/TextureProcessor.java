package org.oryxel.viabedrockutility.pack.processor;

import net.easecation.bedrockmotion.pack.content.Content;
import net.easecation.bedrockmotion.pack.PackManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import org.oryxel.viabedrockutility.util.GeometryUtil;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TextureProcessor {
    private static final Map<ResourceLocation, LazyBedrockTexture> REGISTERED_TEXTURES =
            new ConcurrentHashMap<>();
    private static volatile AlphaSnapshot registeredAlphas = new AlphaSnapshot(null, Map.of());
    private static long alphaRevision;

    // Published Content payload arrays are immutable. Weak identities avoid extending pack/byte
    // lifetimes merely to reuse alpha, while Content.put with replacement bytes invalidates a hit.
    record AlphaEntry(WeakReference<Content> source, String path, WeakReference<byte[]> bytes,
                      GeometryUtil.TextureAlpha alpha) {
        AlphaEntry(Content source, String path, byte[] bytes, GeometryUtil.TextureAlpha alpha) {
            this(new WeakReference<>(source), path, new WeakReference<>(bytes), alpha);
        }
        boolean stillMatches() {
            final Content current = source.get();
            final byte[] expected = bytes.get();
            return current == null || (expected != null && current.get(path) == expected);
        }
    }
    static final class AlphaSnapshot {
        private final WeakReference<PackManager> owner;
        private final Map<ResourceLocation, AlphaEntry> entries;
        AlphaSnapshot(PackManager owner, Map<ResourceLocation, AlphaEntry> entries) {
            this.owner = new WeakReference<>(owner);
            this.entries = Map.copyOf(entries);
        }
        PackManager owner() { return owner.get(); }
        Map<ResourceLocation, AlphaEntry> entries() { return entries; }
    }
    static synchronized long beginAlphaUpdate() { return ++alphaRevision; }
    static synchronized void publishAlphas(long revision, PackManager owner, Map<ResourceLocation, AlphaEntry> entries) {
        if (revision == alphaRevision) registeredAlphas = new AlphaSnapshot(owner, entries);
    }
    /** Failed publication may retire only its own snapshot, never a newer manager's snapshot. */
    public static synchronized void discardAlphas(PackManager owner) {
        if (registeredAlphas.owner() == owner) {
            ++alphaRevision;
            registeredAlphas = new AlphaSnapshot(null, Map.of());
        }
    }
    static synchronized void clearAlphas() {
        ++alphaRevision;
        registeredAlphas = new AlphaSnapshot(null, Map.of());
    }

    public static void process(final List<Content> packs) { process(packs, null); }

    public static void process(final List<Content> packs, final PackManager owner) {
        org.oryxel.viabedrockutility.attachable.AttachableMaterials.clear();
        final long revision = beginAlphaUpdate();
        final Minecraft client = Minecraft.getInstance();
        final Map<ResourceLocation, LazyBedrockTexture> nextGeneration = new LinkedHashMap<>();
        final Map<ResourceLocation, AlphaEntry> nextGenerationAlphas = new LinkedHashMap<>();

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
                    final String sourcePath = content.getFullPath(path, "png", "jpg");
                    nextGenerationAlphas.put(identifier, new AlphaEntry(content, sourcePath, content.get(sourcePath),
                            GeometryUtil.TextureAlpha.from(image.getImage())));
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
                "[ResourcePack:Texture] Registered {} lazy textures ({} active texture(s) queued for preparation, "
                        + "{} metadata sidecars handled by ResourceManager, {} failed)",
                registered, preloaded, metadata, failed
        );

        REGISTERED_TEXTURES.keySet().stream()
                .filter(identifier -> !nextGeneration.containsKey(identifier))
                .forEach(client.getTextureManager()::release);
        REGISTERED_TEXTURES.clear();
        REGISTERED_TEXTURES.putAll(nextGeneration);
        publishAlphas(revision, owner, nextGenerationAlphas);
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

    /** Explicit opt-in preparation; normal rendering keeps its existing missing-image fallback. */
    public static void prepareRequiredTexture(final String rawPath) {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Texture preparation requires render thread");
        final var identifier = normalizeTextureIdentifier(rawPath);
        final var texture = REGISTERED_TEXTURES.get(identifier);
        if (texture == null) throw new IllegalArgumentException("Missing required Bedrock texture: " + rawPath);
        try { texture.requireLoaded(); }
        catch (RuntimeException failure) { throw new IllegalStateException("Cannot prepare Bedrock texture: " + rawPath, failure); }
    }

    /** Returns the CPU-side alpha snapshot for a registered Bedrock texture, when available. */
    public static GeometryUtil.TextureAlpha resolveAlpha(final String rawPath) {
        try {
            final AlphaEntry entry = registeredAlphas.entries().get(normalizeTextureIdentifier(rawPath));
            return entry != null && entry.stillMatches() ? entry.alpha() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** Cache lookup at the current layer; never skips the caller's normal top-to-bottom search. */
    public static GeometryUtil.TextureAlpha resolveAlpha(PackManager owner, Content source, String rawPath) {
        final AlphaSnapshot snapshot = registeredAlphas;
        if (owner == null || snapshot.owner() != owner) return null;
        try {
            final AlphaEntry entry = snapshot.entries().get(normalizeTextureIdentifier(rawPath));
            if (entry == null || entry.source().get() != source || !entry.stillMatches()) return null;
            // PNG/JPG selection and exact-path preference must agree with Content.getShortnameImage.
            return entry.path().equals(source.getFullPath(rawPath, "png", "jpg")) ? entry.alpha() : null;
        } catch (RuntimeException invalid) { return null; }
    }

    /** Fallback for this manager after its source lookup, including registered vanilla base textures. */
    public static GeometryUtil.TextureAlpha resolveAlpha(PackManager owner, String rawPath) {
        final AlphaSnapshot snapshot = registeredAlphas;
        if (owner == null || snapshot.owner() != owner) return null;
        try {
            final AlphaEntry entry = snapshot.entries().get(normalizeTextureIdentifier(rawPath));
            return entry != null && entry.stillMatches() ? entry.alpha() : null;
        } catch (RuntimeException invalid) { return null; }
    }

    /** Releases every dynamic texture owned by the retired server-pack generation. */
    public static void clear() {
        org.oryxel.viabedrockutility.attachable.AttachableMaterials.clear();
        clearAlphas();
        final Minecraft client = Minecraft.getInstance();
        REGISTERED_TEXTURES.keySet().forEach(client.getTextureManager()::release);
        REGISTERED_TEXTURES.clear();
    }
}
