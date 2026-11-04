package org.oryxel.viabedrockutility.animation;

import net.easecation.bedrockmotion.animator.AnimationClock;
import net.easecation.bedrockmotion.entity.ClientEntityAnimationRuntime;
import net.easecation.bedrockmotion.model.AnimationEventListener;
import net.easecation.bedrockmotion.model.IBoneModel;
import net.easecation.bedrockmotion.mocha.LayeredScope;
import net.easecation.bedrockmotion.mocha.DebugBinding;
import net.easecation.bedrockmotion.mocha.MoLangEvaluationContext;
import net.easecation.bedrockmotion.pack.PackManager;
import net.minecraft.client.model.PlayerModel;
import org.oryxel.viabedrockutility.adapter.McBoneModel;
import org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler;
import org.oryxel.viabedrockutility.renderer.BedrockPlayerParentPose;
import team.unnamed.mocha.runtime.ExecutionContext;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.value.Function;
import team.unnamed.mocha.runtime.value.NumberValue;
import team.unnamed.mocha.runtime.value.Value;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Owns the independent first/third-person Bedrock player-host runtimes for one renderer. */
public final class PlayerAnimationRuntime {
    private final ViewInstance firstPerson;
    private final ViewInstance thirdPerson;
    private final MovementDistance movementDistance = new MovementDistance();
    private McBoneModel cachedModel;
    private volatile SampleDebugSnapshot lastSample;

    public PlayerAnimationRuntime(PackManager packs, Map<String, String> animationOverrides) {
        Objects.requireNonNull(packs, "packs");
        final var definition = packs.getEntityDefinitions().getEntities().get("minecraft:player");
        if (definition == null) {
            throw new IllegalArgumentException("PackManager is missing minecraft:player");
        }
        this.firstPerson = new ViewInstance(definition.entityData(), animationOverrides, packs);
        this.thirdPerson = new ViewInstance(definition.entityData(), animationOverrides, packs);
    }

    public static boolean isAvailable(PackManager packs) {
        return packs != null && packs.getEntityDefinitions() != null
                && packs.getEntityDefinitions().getEntities().containsKey("minecraft:player");
    }

    public void sampleFirstPerson(PlayerModel model, PlayerAnimationState state) {
        sample(model, state, firstPerson);
    }

    public void sampleThirdPerson(PlayerModel model, PlayerAnimationState state) {
        sample(model, state, thirdPerson);
        BedrockPlayerParentPose.update(model);
        org.oryxel.viabedrockutility.renderer.BedrockPlayerArmorPose.update(model);
    }

    /** Captures both view runtimes without mutating either runtime or the host model. */
    public DebugSnapshot debugSnapshot() {
        return new DebugSnapshot(firstPerson.debugSnapshot(), thirdPerson.debugSnapshot(), lastSample);
    }

    void sampleFirstPerson(IBoneModel model, PlayerAnimationState state) {
        sample(model, state, firstPerson);
    }

    void sampleThirdPerson(IBoneModel model, PlayerAnimationState state) {
        sample(model, state, thirdPerson);
    }

    public void playAnimation(String animation, String nextState, String stopExpression,
                              String controller, float blendOutTime) {
        firstPerson.runtime.playAnimation(animation, nextState, stopExpression, controller, blendOutTime);
        thirdPerson.runtime.playAnimation(animation, nextState, stopExpression, controller, blendOutTime);
    }

    private void sample(PlayerModel model, PlayerAnimationState state, ViewInstance view) {
        Objects.requireNonNull(state, "state");
        if (cachedModel == null || cachedModel.getModel() != model) {
            cachedModel = new McBoneModel(model);
        }
        resetVanillaHostPose(model);
        sample(cachedModel, state, view);
    }

    private void sample(IBoneModel model, PlayerAnimationState state, ViewInstance view) {
        Objects.requireNonNull(state, "state");
        view.bindModel(model instanceof McBoneModel nativeModel
                && nativeModel.getModel() instanceof PlayerModel playerModel ? playerModel : null);
        view.update(state, movementDistance.update(state));
        try {
            view.runtime.sample(model, state.partialTick(), view.scope, MoLangEvaluationContext.EMPTY);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to sample Bedrock player animation", exception);
        }
        lastSample = new SampleDebugSnapshot(state.view(), state.tick(), state.partialTick(),
                System.identityHashCode(model));
    }

    /**
     * The Bedrock player runtime owns the complete host pose for both views. A first-person sample
     * can follow a third-person setupAnim call on the same PlayerModel, so restoring only rotations
     * leaves vanilla crouch/swim/attack translations (and scales) in the direct arm render path.
     * Model.resetPose() restores every ModelPart to its baked pose; the Bedrock adapter then resets
     * its own rotation/offset/scale state before sampling the authoritative player controller.
     */
    static void resetVanillaHostPose(PlayerModel model) {
        model.resetPose();
    }

    private static final class ViewInstance extends PlayerAnimationInputs {
        private final ClientEntityAnimationRuntime runtime;

        private ViewInstance(org.cube.converter.data.bedrock.BedrockEntityData entity,
                             Map<String, String> animationOverrides, PackManager packs) {
            this.runtime = new ClientEntityAnimationRuntime(entity, animationOverrides, packs,
                    new AnimationClock.Client(), new AnimationEventListener() {
                        @Override public void onTimelineEvent(List<String> expressions) {}
                        @Override public Scope getEntityScope() { return scope; }
                    });
        }

        @Override void update(PlayerAnimationState next, double distanceMoved) {
            super.update(next, distanceMoved);
            runtime.tick(next.tick(), next.partialTick(), scope, MoLangEvaluationContext.EMPTY,
                    ignored -> bindVariables(next));
        }

        private ViewDebugSnapshot debugSnapshot() {
            return new ViewDebugSnapshot(state, runtime.debugSnapshot(),
                    query.debugSnapshot(), variables.debugSnapshot());
        }
    }

    static final class MovementDistance {
        private long tick = Long.MIN_VALUE;
        private float partialTick;
        private double x;
        private double z;
        private double distance;

        double update(PlayerAnimationState state) {
            if (tick == Long.MIN_VALUE) {
                tick = state.tick();
                partialTick = state.partialTick();
                x = state.positionX();
                z = state.positionZ();
            } else if (state.tick() > tick
                    || state.tick() == tick && state.partialTick() > partialTick) {
                if (state.onGround() && !state.crouching()) {
                    distance += Math.hypot(state.positionX() - x, state.positionZ() - z);
                }
                tick = state.tick();
                partialTick = state.partialTick();
                x = state.positionX();
                z = state.positionZ();
            }
            return distance;
        }
    }

    public record DebugSnapshot(ViewDebugSnapshot firstPerson, ViewDebugSnapshot thirdPerson,
                                SampleDebugSnapshot lastSample) {
    }

    public record SampleDebugSnapshot(PlayerAnimationState.View view, long tick,
                                      float partialTick, int boneModelIdentity) {
    }

    public record ViewDebugSnapshot(PlayerAnimationState state,
                                    ClientEntityAnimationRuntime.DebugSnapshot runtime,
                                    Map<String, String> queries,
                                    Map<String, String> variables) {
    }
}
