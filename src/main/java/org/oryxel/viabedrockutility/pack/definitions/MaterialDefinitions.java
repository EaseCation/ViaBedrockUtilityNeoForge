package org.oryxel.viabedrockutility.pack.definitions;

import com.google.gson.JsonParser;
import net.easecation.bedrockmotion.pack.PackManager;
import net.easecation.bedrockmotion.pack.content.Content;
import org.oryxel.viabedrockutility.fabric.ViaBedrockUtilityFabric;
import org.oryxel.viabedrockutility.material.VanillaMaterials;
import org.oryxel.viabedrockutility.material.data.Material;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * VBU-specific MaterialDefinitions that resolves material names to MC Material rendering objects.
 * This is separate from BedrockMotion's simplified MaterialDefinitions.
 */
public class MaterialDefinitions {
    private final Map<String, Material> NAME_TO_MATERIAL = new HashMap<>();

    public MaterialDefinitions(final PackManager packManager) {
        for (final Content content : packManager.getPacks()) {
            if (!content.contains("materials/entity.material")) {
                continue;
            }

            try {
                NAME_TO_MATERIAL.putAll(Material.parse(Collections.unmodifiableMap(VanillaMaterials.NAME_TO_MATERIAL), JsonParser.parseString(content.getString("materials/entity.material")).getAsJsonObject()));
            } catch (Throwable e) {
                ViaBedrockUtilityFabric.LOGGER.warn("Failed to parse entity material!");
            }
        }
    }

    public boolean hasMaterial(final String name) {
        return NAME_TO_MATERIAL.containsKey(name);
    }

    public Material getMaterial(final String name) {
        return NAME_TO_MATERIAL.getOrDefault(name, VanillaMaterials.getMaterial(name));
    }
}
