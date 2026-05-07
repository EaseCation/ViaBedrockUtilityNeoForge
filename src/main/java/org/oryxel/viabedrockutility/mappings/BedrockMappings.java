package org.oryxel.viabedrockutility.mappings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import org.cube.converter.util.GsonUtil;
import org.oryxel.viabedrockutility.enums.bedrock.ActorFlags;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class BedrockMappings {
    @Getter
    private static Map<ActorFlags, String> bedrockEntityFlagMoLangQueries;

    public static void load() {
        final JsonObject entityFlagMoLangQueryMappingsJson = readJson("bedrock/entity_flag_molang_query_mappings.json");
        bedrockEntityFlagMoLangQueries = new EnumMap<>(ActorFlags.class);
        final Set<ActorFlags> unmappedEntityFlags = EnumSet.noneOf(ActorFlags.class);
        for (Map.Entry<String, JsonElement> entry : entityFlagMoLangQueryMappingsJson.entrySet()) {
            final ActorFlags entityFlag = ActorFlags.valueOf(entry.getKey());
            if (entry.getValue().isJsonNull()) {
                unmappedEntityFlags.add(entityFlag);
                continue;
            }
            bedrockEntityFlagMoLangQueries.put(entityFlag, entry.getValue().getAsString());
        }
        for (ActorFlags entityFlag : ActorFlags.values()) {
            if (!bedrockEntityFlagMoLangQueries.containsKey(entityFlag) && !unmappedEntityFlags.contains(entityFlag)) {
                throw new RuntimeException("Missing bedrock MoLang query mapping for " + entityFlag.name());
            }
        }
    }

    private static JsonObject readJson(String file) {
        return readJson(file, JsonObject.class);
    }

    private static  <T> T readJson(String file, final Class<T> classOfT) {
        file = "assets/viabedrockutility/" + file;
        try (final InputStream inputStream = BedrockMappings.class.getClassLoader().getResourceAsStream(file)) {
            if (inputStream == null) {
                return null;
            }

            return GsonUtil.getGson().fromJson(new InputStreamReader(inputStream), classOfT);
        } catch (IOException e) {
            return null;
        }
    }
}
