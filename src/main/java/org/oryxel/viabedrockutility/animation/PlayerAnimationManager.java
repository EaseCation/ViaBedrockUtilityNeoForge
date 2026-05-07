package org.oryxel.viabedrockutility.animation;

import net.easecation.bedrockmotion.animation.vanilla.AnimationHelper;
import net.easecation.bedrockmotion.pack.definitions.AnimationDefinitions;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.adapter.McBoneModel;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;
import org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.Value;

import java.util.*;

public class PlayerAnimationManager {
    /**
     * Specifies which vanilla rotation axes to clear on a bone.
     * Vanilla setAngles() sets pitch(X)/yaw(Y)/roll(Z); we only clear the specific axes
     * that correspond to the vanilla animation being overridden.
     */
    private record BoneClear(String boneName, boolean pitch, boolean yaw, boolean roll) {
    }

    /**
     * Maps animation short names to precise per-bone, per-axis clearing rules.
     * Only axes that vanilla setAngles() actually writes for that animation are cleared.
     * Unlisted animation names are purely additive (no clearing).
     *
     * Vanilla PlayerModel.setAngles() reference:
     *   bob         -> arms roll (Z-axis oscillation: cos(age) * amplitude)
     *   move.arms   -> arms pitch (X-axis swing: cos(limbSwing) * amplitude)
     *   move.legs   -> legs pitch (X-axis swing: cos(limbSwing) * amplitude)
     *   attack.*    -> rightArm pitch (X-axis swing)
     *   riding.*    -> pitch on arms/legs
     *   sneaking    -> body pitch + leg/arm pitch adjustments
     */
    private static final Map<String, List<BoneClear>> VANILLA_CLEAR_MAP = Map.ofEntries(
            Map.entry("bob", List.of(
                    new BoneClear("rightarm", false, false, true),
                    new BoneClear("leftarm", false, false, true)
            )),
            Map.entry("move.arms", List.of(
                    new BoneClear("rightarm", true, false, false),
                    new BoneClear("leftarm", true, false, false)
            )),
            Map.entry("move.legs", List.of(
                    new BoneClear("rightleg", true, false, false),
                    new BoneClear("leftleg", true, false, false)
            )),
            Map.entry("attack.rotations", List.of(
                    new BoneClear("rightarm", true, false, false)
            )),
            Map.entry("riding.arms", List.of(
                    new BoneClear("rightarm", true, false, false),
                    new BoneClear("leftarm", true, false, false)
            )),
            Map.entry("riding.legs", List.of(
                    new BoneClear("rightleg", true, false, false),
                    new BoneClear("leftleg", true, false, false)
            )),
            Map.entry("sneaking", List.of(
                    new BoneClear("body", true, false, false)
            )),
            Map.entry("swimming", List.of(
                    new BoneClear("rightarm", true, true, true),
                    new BoneClear("leftarm", true, true, true),
                    new BoneClear("rightleg", true, false, false),
                    new BoneClear("leftleg", true, false, false),
                    new BoneClear("body", true, false, false)
            )),
            Map.entry("swimming.legs", List.of(
                    new BoneClear("rightleg", true, false, false),
                    new BoneClear("leftleg", true, false, false)
            )),
            Map.entry("crawling", List.of(
                    new BoneClear("rightarm", true, true, true),
                    new BoneClear("leftarm", true, true, true),
                    new BoneClear("rightleg", true, false, false),
                    new BoneClear("leftleg", true, false, false),
                    new BoneClear("body", true, false, false)
            )),
            Map.entry("crawling.legs", List.of(
                    new BoneClear("rightleg", true, false, false),
                    new BoneClear("leftleg", true, false, false)
            ))
    );

    private final Map<String, AnimationDefinitions.AnimationData> animations = new LinkedHashMap<>();
    private final Set<String> affectedBones = new HashSet<>();
    private final long startTimeMS = System.currentTimeMillis();
    private final Vector3f tempVec = new Vector3f();

    public void addAnimation(String shortName, AnimationDefinitions.AnimationData data) {
        animations.put(shortName, data);
        affectedBones.addAll(data.compiled().boneAnimations().keySet());
    }

    public boolean isEmpty() {
        return animations.isEmpty();
    }

    public Set<String> getAffectedBones() {
        return affectedBones;
    }

    /**
     * Called every frame from PlayerModel.setAngles() TAIL injection.
     * For known vanilla-replacing animations, clears only the specific axes that vanilla sets.
     * All other animations are purely additive via IModelPart.rotation.
     */
    @SuppressWarnings("unchecked")
    public void animate(Model model, PlayerRenderState state) {
        List<ModelPart> parts = (List<ModelPart>) model.allParts();

        // Reset affected bones' VBU rotation/offset to default before additive blending
        for (String boneName : affectedBones) {
            getPartByName(parts, boneName)
                .ifPresent(part -> ((IModelPart)((Object)part)).viaBedrockUtility$resetToDefaultPose());
        }

        final McBoneModel boneModel = new McBoneModel(model);
        final Scope scope = buildScope(state);
        final long elapsed = System.currentTimeMillis() - startTimeMS;
        for (Map.Entry<String, AnimationDefinitions.AnimationData> entry : animations.entrySet()) {
            final List<BoneClear> clears = VANILLA_CLEAR_MAP.get(entry.getKey());
            if (clears != null) {
                for (BoneClear bc : clears) {
                    Optional<ModelPart> opt = getPartByName(parts, bc.boneName());
                    opt.ifPresent(part -> {
                        if (bc.pitch()) part.xRot = 0;
                        if (bc.yaw()) part.yRot = 0;
                        if (bc.roll()) part.zRot = 0;
                    });
                }
            }
            AnimationHelper.animate(scope, boneModel, entry.getValue().compiled(), elapsed, 1.0f, tempVec, null);
        }
    }

    private Scope buildScope(PlayerRenderState state) {
        // Start from BASE_SCOPE (contains math binding) to get math.cos, math.sin, etc.
        final Scope scope = CustomEntityPayloadHandler.BASE_SCOPE.copy();

        final MutableObjectBinding query = new MutableObjectBinding();
        query.set("life_time", Value.of(state.ageInTicks / 20.0f));
        query.set("modified_distance_moved", Value.of(state.walkAnimationPos));
        query.set("modified_move_speed", Value.of(state.walkAnimationSpeed));
        query.set("is_on_ground", Value.of(true));
        query.set("is_alive", Value.of(true));

        scope.set("query", query);
        scope.set("q", query);

        return scope;
    }

    /**
     * Finds a ModelPart by name from a flat list of parts (MC-specific helper).
     */
    private static Optional<ModelPart> getPartByName(List<ModelPart> parts, String name) {
        for (ModelPart part : parts) {
            if (((IModelPart)((Object)part)).viaBedrockUtility$getName().equalsIgnoreCase(name) && part.isEmpty()) {
                return Optional.of(part);
            }
        }
        return Optional.empty();
    }
}
