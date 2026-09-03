package org.oryxel.viabedrockutility.diagnostics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.easecation.bedrockmotion.pack.content.Content;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashSet;

/** Read-only provenance index for the exact embedded Bedrock packs consumed by one VBU generation. */
public final class BedrockPackDiagnostics {
    public static final String STACK_MANIFEST_PATH = "bedrock/pack_stack.json";

    private BedrockPackDiagnostics() {
    }

    public enum DefinitionType {
        ENTITY("entity"),
        ATTACHABLE("attachable"),
        ANIMATION("animation"),
        ANIMATION_CONTROLLER("controller"),
        RENDER_CONTROLLER("render_controller"),
        GEOMETRY("geometry"),
        PARTICLE("particle"),
        TEXTURE("texture");

        private final String commandName;

        DefinitionType(String commandName) {
            this.commandName = commandName;
        }

        public String commandName() {
            return commandName;
        }

        public static DefinitionType parse(String value) {
            final String normalized = normalizeCommand(value);
            for (DefinitionType type : values()) {
                if (type.commandName.equals(normalized)
                        || normalizeCommand(type.name()).equals(normalized)) {
                    return type;
                }
            }
            return null;
        }
    }

    public record PackInput(int outerRequestIndex, String outerRequestId, String outerFile,
                            String embeddedPath, Content content, String archiveSha256,
                            boolean orderedByManifest) {
        public PackInput {
            outerRequestId = Objects.requireNonNullElse(outerRequestId, "");
            outerFile = Objects.requireNonNullElse(outerFile, "");
            embeddedPath = Objects.requireNonNullElse(embeddedPath, "");
            content = Objects.requireNonNull(content, "content");
            archiveSha256 = Objects.requireNonNullElse(archiveSha256, "");
        }
    }

    public record PackSource(int loadIndex, int outerRequestIndex, String outerRequestId,
                             String outerFile, String embeddedPath, String packUuid,
                             String packVersion, String packName, String sha256,
                             int fileCount, boolean orderedByManifest) {
    }

    public record DefinitionSource(DefinitionType type, String identifier, int loadIndex,
                                   String embeddedPath, String sourcePath, String packUuid,
                                   String sha256) {
    }

    public record DefinitionKey(DefinitionType type, String identifier) {
        public DefinitionKey {
            Objects.requireNonNull(type, "type");
            identifier = normalizeIdentifier(identifier);
        }
    }

    public record Snapshot(List<PackSource> packs,
                           Map<DefinitionKey, List<DefinitionSource>> definitions,
                           boolean orderedManifest, String orderingWarning) {
        public Snapshot {
            packs = List.copyOf(packs);
            final Map<DefinitionKey, List<DefinitionSource>> copy = new LinkedHashMap<>();
            definitions.forEach((key, value) -> copy.put(key, List.copyOf(value)));
            definitions = Map.copyOf(copy);
            orderingWarning = Objects.requireNonNullElse(orderingWarning, "");
        }

        public static Snapshot empty() {
            return new Snapshot(List.of(), Map.of(), false, "No Bedrock pack generation is active");
        }

        public DefinitionSource winner(DefinitionType type, String identifier) {
            final List<DefinitionSource> sources = definitions.get(new DefinitionKey(type, identifier));
            return sources == null || sources.isEmpty() ? null : sources.getLast();
        }

        public List<DefinitionSource> sources(DefinitionType type, String identifier) {
            return definitions.getOrDefault(new DefinitionKey(type, identifier), List.of());
        }

        public long conflictCount() {
            return definitions.values().stream().filter(values -> values.size() > 1).count();
        }

        public List<Map.Entry<DefinitionKey, List<DefinitionSource>>> conflicts() {
            return definitions.entrySet().stream()
                    .filter(entry -> entry.getValue().size() > 1)
                    .sorted(Comparator.comparing((Map.Entry<DefinitionKey, List<DefinitionSource>> entry) ->
                                    entry.getKey().type().commandName())
                            .thenComparing(entry -> entry.getKey().identifier()))
                    .toList();
        }
    }

    public record StackOrder(List<String> paths, boolean manifestPresent,
                             boolean manifestApplied, String warning) {
        public StackOrder {
            paths = List.copyOf(paths);
            warning = Objects.requireNonNullElse(warning, "");
        }
    }

    /** Resolves one converted Java pack's embedded Bedrock stack in bottom-to-top order. */
    public static StackOrder resolveEmbeddedPackOrder(Content outer) {
        final List<String> discovered = outer.getFilesDeep("bedrock/", ".mcpack");
        if (!outer.contains(STACK_MANIFEST_PATH)) {
            return new StackOrder(discovered, false, false,
                    discovered.size() > 1
                            ? "Multiple embedded mcpack files have no ordered stack manifest"
                            : "");
        }
        try {
            final JsonObject manifest = outer.getJson(STACK_MANIFEST_PATH);
            if (manifest == null || !manifest.has("format_version")
                    || manifest.get("format_version").getAsInt() != 1
                    || !manifest.has("order")
                    || !"bottom_to_top".equals(manifest.get("order").getAsString())) {
                throw new IllegalArgumentException("unsupported format or order");
            }
            final JsonArray entries = manifest.getAsJsonArray("packs");
            if (entries == null) {
                throw new IllegalArgumentException("missing packs array");
            }
            final List<String> ordered = new ArrayList<>();
            final LinkedHashSet<String> unique = new LinkedHashSet<>();
            for (JsonElement element : entries) {
                if (!element.isJsonObject() || !element.getAsJsonObject().has("path")) {
                    throw new IllegalArgumentException("pack entry has no path");
                }
                final String path = element.getAsJsonObject().get("path").getAsString();
                if (!path.startsWith("bedrock/") || !path.endsWith(".mcpack")
                        || !outer.contains(path) || !unique.add(path)) {
                    throw new IllegalArgumentException("invalid or duplicate pack path " + path);
                }
                ordered.add(path);
            }
            if (!unique.equals(new LinkedHashSet<>(discovered))) {
                throw new IllegalArgumentException("manifest does not cover every embedded mcpack");
            }
            return new StackOrder(ordered, true, true, "");
        } catch (Throwable error) {
            return new StackOrder(discovered, true, false,
                    "Invalid ordered stack manifest: " + error.getMessage());
        }
    }

    public static Snapshot inspect(List<PackInput> inputs, boolean orderedManifest,
                                   String orderingWarning) {
        final List<PackSource> packs = new ArrayList<>();
        final Map<DefinitionKey, List<DefinitionSource>> definitions = new LinkedHashMap<>();
        for (int loadIndex = 0; loadIndex < inputs.size(); loadIndex++) {
            final PackInput input = inputs.get(loadIndex);
            final JsonObject manifest = safeJson(input.content(), "manifest.json");
            final JsonObject header = manifest == null ? null : manifest.getAsJsonObject("header");
            final String uuid = string(header, "uuid");
            final String version = version(header);
            final String name = string(header, "name");
            final String sha256 = input.archiveSha256();
            final PackSource pack = new PackSource(loadIndex, input.outerRequestIndex(),
                    input.outerRequestId(), input.outerFile(), input.embeddedPath(), uuid,
                    version, name, sha256, input.content().size(), input.orderedByManifest());
            packs.add(pack);
            scan(input.content(), pack, definitions);
        }
        return new Snapshot(packs, definitions, orderedManifest, orderingWarning);
    }

    private static void scan(Content content, PackSource pack,
                             Map<DefinitionKey, List<DefinitionSource>> definitions) {
        scanObjectIdentifiers(content, pack, definitions, DefinitionType.ANIMATION,
                "animations/", "animations");
        scanObjectIdentifiers(content, pack, definitions, DefinitionType.ANIMATION_CONTROLLER,
                "animation_controllers/", "animation_controllers");
        scanObjectIdentifiers(content, pack, definitions, DefinitionType.RENDER_CONTROLLER,
                "render_controllers/", "render_controllers");

        for (String path : sorted(content.getFilesDeep("entity/", ".json"))) {
            final JsonObject root = safeJson(content, path);
            final JsonObject entity = root == null ? null : root.getAsJsonObject("minecraft:client_entity");
            addDescriptionIdentifier(definitions, pack, DefinitionType.ENTITY, entity, path);
        }
        for (String path : sorted(content.getFilesDeep("attachables/", ".json"))) {
            final JsonObject root = safeJson(content, path);
            final JsonObject attachable = root == null ? null : root.getAsJsonObject("minecraft:attachable");
            addDescriptionIdentifier(definitions, pack, DefinitionType.ATTACHABLE, attachable, path);
        }
        for (String path : sorted(content.getFilesDeep("models/", ".json"))) {
            final JsonObject root = safeJson(content, path);
            final JsonArray geometries = root == null ? null : root.getAsJsonArray("minecraft:geometry");
            if (geometries == null) continue;
            for (JsonElement element : geometries) {
                if (!element.isJsonObject()) continue;
                final JsonObject description = element.getAsJsonObject().getAsJsonObject("description");
                add(definitions, pack, DefinitionType.GEOMETRY, string(description, "identifier"), path);
            }
        }
        for (String path : sorted(content.getFilesDeep("particles/", ".json"))) {
            final JsonObject root = safeJson(content, path);
            final JsonObject particle = root == null ? null : root.getAsJsonObject("particle_effect");
            addDescriptionIdentifier(definitions, pack, DefinitionType.PARTICLE, particle, path);
        }
        for (String path : sorted(content.getFilesDeep("textures/", ""))) {
            final String lower = path.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".png") || lower.endsWith(".tga")
                    || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                add(definitions, pack, DefinitionType.TEXTURE,
                        path.substring(0, path.lastIndexOf('.')), path);
            }
        }
    }

    private static void scanObjectIdentifiers(Content content, PackSource pack,
                                              Map<DefinitionKey, List<DefinitionSource>> definitions,
                                              DefinitionType type, String directory, String objectName) {
        for (String path : sorted(content.getFilesDeep(directory, ".json"))) {
            final JsonObject root = safeJson(content, path);
            final JsonObject values = root == null ? null : root.getAsJsonObject(objectName);
            if (values == null) continue;
            for (String identifier : values.keySet()) {
                add(definitions, pack, type, identifier, path);
            }
        }
    }

    private static void addDescriptionIdentifier(
            Map<DefinitionKey, List<DefinitionSource>> definitions,
            PackSource pack, DefinitionType type, JsonObject object, String path) {
        final JsonObject description = object == null ? null : object.getAsJsonObject("description");
        add(definitions, pack, type, string(description, "identifier"), path);
    }

    private static void add(Map<DefinitionKey, List<DefinitionSource>> definitions,
                            PackSource pack, DefinitionType type, String identifier, String path) {
        if (identifier == null || identifier.isBlank()) return;
        final DefinitionKey key = new DefinitionKey(type, identifier);
        definitions.computeIfAbsent(key, ignored -> new ArrayList<>()).add(
                new DefinitionSource(type, identifier, pack.loadIndex(), pack.embeddedPath(),
                        path, pack.packUuid(), pack.sha256()));
    }

    private static JsonObject safeJson(Content content, String path) {
        try {
            return content.getJson(path);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<String> sorted(List<String> paths) {
        return paths.stream().sorted().toList();
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return "";
        return object.get(key).getAsString();
    }

    private static String version(JsonObject header) {
        if (header == null || !header.has("version")) return "";
        final JsonElement value = header.get("version");
        if (!value.isJsonArray()) return value.toString();
        final List<String> parts = new ArrayList<>();
        value.getAsJsonArray().forEach(element -> parts.add(element.getAsString()));
        return String.join(".", parts);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static String shortHash(String value) {
        return value == null || value.length() <= 12 ? Objects.requireNonNullElse(value, "")
                : value.substring(0, 12);
    }

    public static String hashText(String value) {
        return sha256(Objects.requireNonNullElse(value, "").getBytes(StandardCharsets.UTF_8));
    }

    public static String hashBytes(byte[] value) {
        return sha256(value == null ? new byte[0] : value);
    }

    private static String normalizeIdentifier(String value) {
        return Objects.requireNonNullElse(value, "").trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeCommand(String value) {
        return normalizeIdentifier(value).replace('-', '_');
    }
}
