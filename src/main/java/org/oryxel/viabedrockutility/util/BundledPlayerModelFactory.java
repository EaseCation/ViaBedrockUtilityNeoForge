package org.oryxel.viabedrockutility.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.model.PlayerModel;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Builds classic player skins through the same Bedrock geometry path as custom skin geometry. */
public final class BundledPlayerModelFactory {
    private static final String PACK_RESOURCE =
            "/assets/viabedrockutility/vanilla_packs/vanilla.mcpack";
    private static final String GEOMETRY_ENTRY = "models/entity/humanoid.custom.geo.json";
    private static final String WIDE_IDENTIFIER = "geometry.humanoid.custom";
    private static final String SLIM_IDENTIFIER = "geometry.humanoid.customSlim";

    private BundledPlayerModelFactory() {
    }

    public static PlayerModel create(boolean slim) {
        final BedrockGeometryModel geometry = geometry(slim);
        return (PlayerModel) GeometryUtil.buildModel(
                geometry, true, slim, geometry.getIdentifier());
    }

    static BedrockGeometryModel geometry(boolean slim) {
        final JsonObject root = readBundledGeometry();
        if (slim) {
            convertToSlim(root);
        }
        final String identifier = slim ? SLIM_IDENTIFIER : WIDE_IDENTIFIER;
        final List<BedrockGeometryModel> geometries = BedrockGeometryModel.fromJson(root);
        final BedrockGeometryModel geometry = geometries.stream()
                .filter(candidate -> identifier.equals(candidate.getIdentifier()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Bundled player geometry is missing " + identifier));
        return geometry;
    }

    private static JsonObject readBundledGeometry() {
        final InputStream resource = BundledPlayerModelFactory.class.getResourceAsStream(PACK_RESOURCE);
        if (resource == null) {
            throw new IllegalStateException("Bundled vanilla pack is missing: " + PACK_RESOURCE);
        }
        try (resource; ZipInputStream zip = new ZipInputStream(resource)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (GEOMETRY_ENTRY.equals(entry.getName())) {
                    return JsonParser.parseString(
                            new String(zip.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read bundled player geometry", exception);
        }
        throw new IllegalStateException("Bundled vanilla pack is missing " + GEOMETRY_ENTRY);
    }

    /** Applies the vanilla customSlim arm and item-bone dimensions to the bundled wide geometry. */
    private static void convertToSlim(JsonObject root) {
        final JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
        if (geometries == null || geometries.isEmpty()) {
            throw new IllegalStateException("Bundled player geometry has no minecraft:geometry entries");
        }
        final JsonObject geometry = geometries.get(0).getAsJsonObject();
        geometry.getAsJsonObject("description").addProperty("identifier", SLIM_IDENTIFIER);
        for (var element : geometry.getAsJsonArray("bones")) {
            final JsonObject bone = element.getAsJsonObject();
            final String name = bone.get("name").getAsString().toLowerCase(Locale.ROOT);
            switch (name) {
                case "leftarm", "leftsleeve" -> makeSlimArm(bone, false);
                case "rightarm", "rightsleeve" -> makeSlimArm(bone, true);
                case "leftitem", "rightitem" -> set(bone.getAsJsonArray("pivot"), 1, 14.5F);
                default -> {
                }
            }
        }
    }

    private static void makeSlimArm(JsonObject bone, boolean right) {
        set(bone.getAsJsonArray("pivot"), 1, 21.5F);
        final JsonArray cubes = bone.getAsJsonArray("cubes");
        if (cubes == null) {
            return;
        }
        for (var element : cubes) {
            final JsonObject cube = element.getAsJsonObject();
            final JsonArray origin = cube.getAsJsonArray("origin");
            if (right) {
                set(origin, 0, -7.0F);
            }
            set(origin, 1, 11.5F);
            set(cube.getAsJsonArray("size"), 0, 3.0F);
        }
    }

    private static void set(JsonArray values, int index, float value) {
        if (values == null || values.size() <= index) {
            throw new IllegalStateException("Bundled player geometry has an incomplete vector");
        }
        values.set(index, new JsonPrimitive(value));
    }
}
