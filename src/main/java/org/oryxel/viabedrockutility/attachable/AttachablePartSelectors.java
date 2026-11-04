package org.oryxel.viabedrockutility.attachable;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Per-model selector topology. Values stay live; only names and ordered rule keys are cached. */
final class AttachablePartSelectors {
    record Match(ModelPart part, List<String> keys) {}

    private final ModelPart[] parts;
    private final boolean includeCubeGroups;
    private List<String> keys = List.of();
    private List<Match> matches = List.of();

    AttachablePartSelectors(Model model, boolean includeCubeGroups) {
        this.parts = model.allParts().toArray(ModelPart[]::new);
        this.includeCubeGroups = includeCubeGroups;
    }

    List<Match> resolve(Map<String, ?> rules) {
        if (sameOrderedKeys(rules)) return matches;
        // Build a complete candidate before publishing; malformed patterns retain no partial plan.
        final List<String> candidateKeys = List.copyOf(rules.keySet());
        final Pattern[] patterns = new Pattern[candidateKeys.size()];
        for (int i = 0; i < patterns.length; i++) {
            String key = candidateKeys.get(i);
            // Preserve the existing selector semantics, including case handling and regex syntax.
            // '*' was an unconditional match, even for names containing line terminators.
            patterns[i] = key.equals("*") ? null
                    : Pattern.compile("(?i)" + key.replace(".", "\\.").replace("*", ".*"));
        }
        final List<Match> candidate = new ArrayList<>();
        for (ModelPart part : parts) {
            final IModelPart extension = (IModelPart) (Object) part;
            final String name = extension.viaBedrockUtility$getName();
            if (name == null || !includeCubeGroups && extension.viaBedrockUtility$isCubeGroup()) continue;
            final List<String> matched = new ArrayList<>();
            for (int i = 0; i < patterns.length; i++) {
                if (patterns[i] == null || patterns[i].matcher(name).matches()) matched.add(candidateKeys.get(i));
            }
            // Material selection also needs unmatched parts, which must remain excluded.
            candidate.add(new Match(part, List.copyOf(matched)));
        }
        keys = candidateKeys;
        matches = List.copyOf(candidate);
        return matches;
    }

    private boolean sameOrderedKeys(Map<String, ?> rules) {
        if (keys.size() != rules.size()) return false;
        int i = 0;
        for (String key : rules.keySet()) if (!keys.get(i++).equals(key)) return false;
        return true;
    }
}
