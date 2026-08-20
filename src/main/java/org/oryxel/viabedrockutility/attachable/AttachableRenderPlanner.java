package org.oryxel.viabedrockutility.attachable;

import net.easecation.bedrockmotion.mocha.MoLangEngine;
import net.easecation.bedrockmotion.mocha.MoLangEvaluationContext;
import net.easecation.bedrockmotion.render.RenderControllerEvaluator;
import net.minecraft.resources.ResourceLocation;
import org.cube.converter.model.element.Parent;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.value.StringValue;
import team.unnamed.mocha.runtime.value.Value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Phase-1 (preflight) planning for attachable rendering: resolves each pass' geometry, per-root
 * bone bindings, host bones and texture without submitting a single vertex, so a fatal failure
 * vetoes the whole render before any pass can commit partial geometry.
 */
final class AttachableRenderPlanner {
    private AttachableRenderPlanner() {
    }

    record PlannedPass<B>(RenderControllerEvaluator.EvaluatedRenderPass pass,
                          BedrockGeometryModel geometry, ResourceLocation texture,
                          LinkedHashMap<String, String> rootBindings,
                          LinkedHashMap<String, HostGroup<B>> hostGroups) {
    }

    /** Every geometry root sharing one resolved host bone, rendered together under that host. */
    record HostGroup<B>(B hostBone, List<String> roots) {
    }

    /**
     * Validates every pass. Returns the planned passes (possibly empty when every pass referenced a
     * missing geometry), or null on the first fatal failure; fatalDetail then receives the reason.
     */
    static <B> List<PlannedPass<B>> plan(List<RenderControllerEvaluator.EvaluatedRenderPass> passes,
                                         Function<String, BedrockGeometryModel> geometryLookup,
                                         Predicate<String> knownHostBone, String defaultHostBone,
                                         Function<String, B> hostBoneLookup,
                                         Scope scope, MoLangEvaluationContext context,
                                         BiConsumer<String, String> warnings,
                                         Consumer<String> fatalDetail) throws IOException {
        final List<PlannedPass<B>> planned = new ArrayList<>(passes.size());
        for (RenderControllerEvaluator.EvaluatedRenderPass pass : passes) {
            final BedrockGeometryModel geometry = geometryLookup.apply(pass.geometryValue());
            if (geometry == null) {
                // Non-fatal: the pass is skipped exactly like the legacy render loop did.
                warnings.accept("geometry:" + pass.geometryValue(), "Missing geometry " + pass.geometryValue());
                continue;
            }
            final ResourceLocation texture;
            try {
                texture = ResourceLocation.parse(pass.textureValue().toLowerCase(Locale.ROOT));
            } catch (RuntimeException exception) {
                fatalDetail.accept("Invalid texture identifier: " + pass.textureValue());
                warnings.accept("texture:" + pass.textureValue(),
                        "Invalid texture identifier '" + pass.textureValue()
                                + "'; attachable rendering is suppressed");
                return null;
            }
            final LinkedHashMap<String, String> rootBindings = resolveRootBindings(geometry,
                    knownHostBone, defaultHostBone, scope, context, warnings);
            if (rootBindings.isEmpty()) {
                warnings.accept("roots:" + pass.geometryValue(),
                        "Geometry '" + pass.geometryValue()
                                + "' has no root bones and cannot be attached");
                continue;
            }
            final LinkedHashMap<String, HostGroup<B>> hostGroups = new LinkedHashMap<>();
            for (Map.Entry<String, String> binding : rootBindings.entrySet()) {
                HostGroup<B> group = hostGroups.get(binding.getValue());
                if (group == null) {
                    final B hostBone = hostBoneLookup.apply(binding.getValue());
                    if (hostBone == null) {
                        fatalDetail.accept("Host binding bone is missing: " + binding.getValue());
                        warnings.accept("binding:" + binding.getValue(),
                        "Missing host binding bone '" + binding.getValue()
                                + "'; attachable rendering is suppressed");
                        return null;
                    }
                    group = new HostGroup<>(hostBone, new ArrayList<>());
                    hostGroups.put(binding.getValue(), group);
                }
                group.roots().add(binding.getKey());
            }
            planned.add(new PlannedPass<>(pass, geometry, texture, rootBindings, hostGroups));
        }
        return planned;
    }

    /**
     * Maps every root bone of the geometry to its host binding. The literal-vs-expression decision
     * is explicit: an exact known host bone name stays a literal, anything else is evaluated as a
     * MoLang expression (so context ternaries such as {@code c.is_first_person ? 'a' : 'b'} are no
     * longer mistaken for bone names). Bindings on non-root bones are ignored with a warning.
     */
    static LinkedHashMap<String, String> resolveRootBindings(BedrockGeometryModel geometry,
                                                             Predicate<String> knownHostBone,
                                                             String defaultHostBone,
                                                             Scope scope, MoLangEvaluationContext context,
                                                             BiConsumer<String, String> warnings) throws IOException {
        final LinkedHashMap<String, String> bindings = new LinkedHashMap<>();
        for (Parent bone : geometry.getParents()) {
            if (!bone.getParent().isBlank()) {
                if (!bone.getBinding().isBlank()) {
                    warnings.accept("non-root-binding:" + bone.getName(),
                            "Non-root binding on bone '" + bone.getName() + "' is ignored");
                }
                continue;
            }
            String binding = bone.getBinding();
            if (binding.isBlank()) {
                binding = defaultHostBone;
            } else if (!knownHostBone.test(binding)) {
                try {
                    final Value evaluated = MoLangEngine.eval(scope, context, binding);
                    if (evaluated instanceof StringValue) {
                        binding = evaluated.getAsString();
                    } else {
                        // Mocha leniently coerces an unresolvable bare name to 0.0; that means the
                        // binding was a (misspelled) literal bone name, not an expression.
                        warnings.accept("binding-eval:" + binding,
                                "Binding expression '" + binding
                                        + "' did not evaluate to a bone name; treating it as a literal bone name");
                    }
                } catch (Throwable throwable) {
                    // Keeping the text as-is yields the proper "missing host bone" fatal instead
                    // of an opaque MoLang error.
                    warnings.accept("binding-eval:" + binding,
                            "Binding expression '" + binding + "' failed to evaluate; treating it as a literal bone name");
                }
            }
            bindings.put(bone.getName(), binding);
        }
        return bindings;
    }
}
