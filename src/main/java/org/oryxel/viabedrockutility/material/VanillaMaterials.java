package org.oryxel.viabedrockutility.material;

import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;
import org.oryxel.viabedrockutility.fabric.ViaBedrockUtilityFabric;
import org.oryxel.viabedrockutility.material.data.Material;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

// https://wiki.bedrock.dev/documentation/materials
public class VanillaMaterials {
    public static final Map<String, Material> NAME_TO_MATERIAL = new HashMap<>();

    public static void init() {
        final InputStream stream = VanillaMaterials.class.getResourceAsStream("/assets/viabedrockutility/vanilla_packs/entity.material");
        if (stream == null) {
            ViaBedrockUtilityFabric.LOGGER.error("Failed to find vanilla material file!");
            return;
        }

        final String content;
        try {
            content = IOUtils.toString(stream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ViaBedrockUtilityFabric.LOGGER.error("Failed to read vanilla material file content!");
            return;
        }

        try {
            NAME_TO_MATERIAL.putAll(Material.parse(new HashMap<>(), JsonParser.parseString(content).getAsJsonObject()));
        } catch (Exception exception) {
            ViaBedrockUtilityFabric.LOGGER.error("Failed to parse vanilla material file!");
            throw new RuntimeException(exception);
        }
    }

    public static Material getMaterial(final String name) {
        return NAME_TO_MATERIAL.getOrDefault(name, NAME_TO_MATERIAL.get("entity"));
    }
}
