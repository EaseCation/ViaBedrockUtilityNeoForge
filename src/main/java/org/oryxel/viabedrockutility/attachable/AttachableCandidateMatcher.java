package org.oryxel.viabedrockutility.attachable;

import net.easecation.bedrockmotion.mocha.MoLangEngine;
import net.easecation.bedrockmotion.pack.PackManager;
import net.easecation.bedrockmotion.pack.definitions.AttachableDefinitions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;

import java.util.ArrayList;
import java.util.List;

/** Matches an owner-held item against indexed attachable definitions by evaluating item conditions. */
final class AttachableCandidateMatcher {

    private AttachableCandidateMatcher() {
    }

    static Candidate match(PackManager packs, AttachableOwnerSnapshot owner, Entity ownerEntity,
                           AttachableItemSnapshot item,
                           AttachableQueryContext.LogicalHand hand, HumanoidArm arm,
                           AttachableQueryContext.ViewContext view, long tick, float partialTick) {
        final List<Candidate> matches = new ArrayList<>();
        for (AttachableDefinitions.AttachableDefinition definition :
                packs.getAttachableDefinitions().candidatesFor(item.itemIdentifier().toString())) {
            final AttachableScopeFactory.RuntimeScope scope = AttachableScopeFactory.RuntimeScope.temporary(
                    owner, ownerEntity, item, hand, arm, view, tick, partialTick, definition.identifier());
            final String condition = definition.data().getItemConditions().get(item.itemIdentifier().toString());
            try {
                if (condition == null || condition.isBlank()
                        || MoLangEngine.eval(scope.scope(), scope.context(), condition).getAsBoolean()) {
                    matches.add(new Candidate(definition));
                }
            } catch (Throwable throwable) {
                AttachableDebugLog.warnOnce(definition.sourcePath() + ":item:" + condition,
                        "[Attachable] Item condition failed in " + definition.sourcePath(), throwable);
            }
        }
        if (matches.size() > 1) {
            final String identifiers = matches.stream().map(match -> match.definition().identifier()).toList().toString();
            AttachableDebugLog.warnOnce(item.itemIdentifier() + ":ambiguous:" + identifiers,
                    "[Attachable] Multiple definitions matched " + item.itemIdentifier() + ": " + identifiers,
                    null);
        }
        return matches.isEmpty() ? null : matches.getFirst();
    }

    record Candidate(AttachableDefinitions.AttachableDefinition definition) {
    }
}
