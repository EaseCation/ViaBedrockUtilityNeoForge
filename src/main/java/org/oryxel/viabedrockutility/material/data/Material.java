package org.oryxel.viabedrockutility.material.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;

import java.util.*;
import java.util.function.Function;

import static net.easecation.bedrockmotion.util.JsonUtil.*;

// https://wiki.bedrock.dev/visuals/materials
public record Material(String identifier, String baseIdentifier, MaterialInfo info) {
    public static Map<String, Material> parse(final Map<String, Material> existing, final JsonObject base) {
        final Map<String, Material> map = new HashMap<>();

        if (!base.has("materials")) {
            return map;
        }

        final JsonObject materials = base.getAsJsonObject("materials");
        for (final String elementName : materials.keySet()) {
            final JsonElement element = materials.get(elementName);
            if (elementName.equals("version") || !element.isJsonObject()) {
                continue;
            }

            final JsonObject object = element.getAsJsonObject();
            final String[] split = elementName.split(":");
            if (split.length > 2) {
                continue;
            }

            final String identifier = split[0], baseIdentifier = split.length == 1 ? "" : split[1];

            final MaterialInfo material;
            if (!baseIdentifier.isBlank()) {
                final Material parent = map.getOrDefault(baseIdentifier, existing.get(baseIdentifier));
                if (parent == null) {
                    material = MaterialInfo.emptyMaterial();
                } else {
                    material = parent.info.clone();
                }
            } else {
                material = MaterialInfo.emptyMaterial();
            }

            material.parse(object, false);
            map.put(identifier, new Material(identifier, baseIdentifier, material));
        }

        return map;
    }

    @RequiredArgsConstructor
    @ToString
    @Getter
    @Setter
    public static class MaterialInfo {
        protected final Set<String> states, defines;
        protected final Set<String> vertexFields;
        protected String vertexShader = "", fragmentShader = "";
        protected String blendSrc = "", blendDst = "", depthFunc = "";

        protected final Map<String, Variant> variants = new HashMap<>();

        private Function<ResourceLocation, RenderType> function;

        public void parse(final JsonObject object, boolean ignoreVariants) {
            final Set<String> extraStates = arrayToStringSet(object.getAsJsonArray("+states"));
            final Set<String> extraDefines = arrayToStringSet(object.getAsJsonArray("+defines"));
            final Set<String> removeStates = arrayToStringSet(object.getAsJsonArray("-states"));
            final Set<String> removeDefines = arrayToStringSet(object.getAsJsonArray("-defines"));

            this.states.addAll(extraStates);
            this.defines.addAll(extraDefines);
            this.states.removeAll(removeStates);
            this.defines.removeAll(removeDefines);

            if (object.has("vertexShader")) {
                this.vertexShader = object.get("vertexShader").getAsString();
            }

            if (object.has("fragmentShader")) {
                this.fragmentShader = object.get("fragmentShader").getAsString();
            }

            if (object.has("vertexFields")) {
                this.vertexFields.clear();
                this.vertexFields.addAll(vertexFields(object.getAsJsonArray("vertexFields")));
            }

            if (object.has("blendSrc")) {
                this.blendSrc = object.get("blendSrc").getAsString();
            }

            if (object.has("blendDst")) {
                this.blendDst = object.get("blendDst").getAsString();
            }

            if (object.has("depthFunc")) {
                this.depthFunc = object.get("depthFunc").getAsString();
            }

            if (ignoreVariants) {
                return;
            }

            if (object.has("variants") && object.get("variants").isJsonArray()) {
                final JsonArray jsonVariants = object.getAsJsonArray("variants");
                for (JsonElement variantElement : jsonVariants) {
                    if (!variantElement.isJsonObject()) {
                        continue;
                    }

                    final JsonObject variantObject = variantElement.getAsJsonObject();
                    for (final String variantName : variantObject.keySet()) {
                        if (!variantObject.get(variantName).isJsonObject()) {
                            continue;
                        }

                        final Variant baseVariant = this.variants.getOrDefault(variantName, MaterialInfo.emptyVariant()).clone();
                        this.variants.put(variantName, baseVariant);

                        baseVariant.parse(variantObject.getAsJsonObject(variantName), true);
                    }
                }
            }

            this.variants.forEach((k, v) -> {
                v.states.addAll(extraStates);
                v.defines.addAll(extraDefines);
                v.states.removeAll(removeStates);
                v.defines.removeAll(removeDefines);

                v.vertexFields.addAll(vertexFields);

                v.fragmentShader = fragmentShader;
                v.vertexShader = vertexShader;

                v.blendSrc = blendSrc;
                v.blendDst = blendDst;

                v.depthFunc = depthFunc;
            });
        }

        public Function<ResourceLocation, RenderType> build() {
            return Objects.requireNonNullElseGet(this.function, () -> this.function = Util.memoize(texture -> {
                // Use standard MC RenderType factory methods so that shader mods (Iris, etc.)
                // can recognize the RenderPipeline singleton and apply gbuffer programs correctly.
                // Primary split on DisableCulling: outline/one-sided techniques require cull=true.
                // USE_EMISSIVE is handled in the renderer via fullbright light override, not here.
                final boolean noCull = this.states.contains("DisableCulling");
                if (this.states.contains("Blending")) {
                    return noCull ? RenderType.entityTranslucent(texture)
                                  : RenderType.itemEntityTranslucentCull(texture);
                } else if (this.defines.contains("ALPHA_TEST")) {
                    return noCull ? RenderType.entityCutoutNoCull(texture)
                                  : RenderType.entityCutout(texture);
                } else {
                    return RenderType.entitySolid(texture);
                }
            }));
        }

        public static MaterialInfo emptyMaterial() {
            return new MaterialInfo(new HashSet<>(), new HashSet<>(), new HashSet<>());
        }

        public static Variant emptyVariant() {
            return new Variant(new HashSet<>(), new HashSet<>(), new HashSet<>());
        }

        public static class Variant extends MaterialInfo {
            public Variant(Set<String> states, Set<String> defines, Set<String> vertexFields) {
                super(states, defines, vertexFields);
            }

            @Override
            public Variant clone() {
                final Variant info = new Variant(new HashSet<>(states), new HashSet<>(defines), new HashSet<>(vertexFields));
                info.setBlendDst(blendDst);
                info.setBlendSrc(blendSrc);
                info.setDepthFunc(depthFunc);
                info.setVertexShader(vertexShader);
                info.setFragmentShader(fragmentShader);
                return info;
            }
        }

        @Override
        public MaterialInfo clone() {
            final MaterialInfo info = new MaterialInfo(new HashSet<>(states), new HashSet<>(defines), new HashSet<>(vertexFields));
            info.setBlendDst(blendDst);
            info.setBlendSrc(blendSrc);
            info.setDepthFunc(depthFunc);
            info.setVertexShader(vertexShader);
            info.setFragmentShader(fragmentShader);
            variants.forEach((k, v) -> {
                info.variants.put(k, v.clone());
            });

            return info;
        }
    }
}
