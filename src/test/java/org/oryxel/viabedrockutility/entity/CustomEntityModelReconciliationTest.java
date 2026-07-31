package org.oryxel.viabedrockutility.entity;

import net.easecation.bedrockmotion.render.RenderControllerEvaluator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomEntityModelReconciliationTest {
    @Test
    void skinChangeReusesModelsWhoseEvaluatedKeysDidNotChange() {
        final Set<String> bedWarsModels = Set.of(
                "frame_frame",
                "title_bedwars",
                "icon_icon",
                "number_digits"
        );
        final List<RenderControllerEvaluator.EvaluatedModel> skyWarsModels = List.of(
                model("frame_frame"),
                model("title_sw"),
                model("icon_icon"),
                model("number_digits")
        );

        final CustomEntityTicker.ModelReconciliation reconciliation =
                CustomEntityTicker.reconcileModelKeys(bedWarsModels, skyWarsModels);

        assertEquals(Set.of("frame_frame", "icon_icon", "number_digits"), reconciliation.retainedKeys());
        assertEquals(Set.of("title_sw"), reconciliation.addedKeys());
        assertEquals(Set.of("title_bedwars"), reconciliation.removedKeys());
    }

    @Test
    void repeatedModelRequestDoesNotAddOrRemoveModels() {
        final Set<String> currentModels = Set.of(
                "frame_frame",
                "title_bedwars",
                "icon_icon",
                "number_digits"
        );
        final List<RenderControllerEvaluator.EvaluatedModel> repeatedModels = currentModels.stream()
                .map(CustomEntityModelReconciliationTest::model)
                .toList();

        final CustomEntityTicker.ModelReconciliation reconciliation =
                CustomEntityTicker.reconcileModelKeys(currentModels, repeatedModels);

        assertEquals(currentModels, reconciliation.retainedKeys());
        assertEquals(Set.of(), reconciliation.addedKeys());
        assertEquals(Set.of(), reconciliation.removedKeys());
    }

    private static RenderControllerEvaluator.EvaluatedModel model(final String key) {
        return new RenderControllerEvaluator.EvaluatedModel(key, null, "geometry." + key, "texture." + key);
    }
}
