package org.oryxel.viabedrockutility.animation;

import net.easecation.bedrockmotion.animation.vanilla.AnimationHelper;
import net.easecation.bedrockmotion.animator.AnimationClock;
import net.easecation.bedrockmotion.entity.ClientEntityAnimationRuntime;
import net.easecation.bedrockmotion.model.AnimationEventListener;
import net.easecation.bedrockmotion.model.IBoneModel;
import net.easecation.bedrockmotion.mocha.LayeredScope;
import net.easecation.bedrockmotion.mocha.MoLangEvaluationContext;
import net.easecation.bedrockmotion.pack.PackManager;
import net.easecation.bedrockmotion.pack.definitions.AnimationDefinitions;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.adapter.McBoneModel;
import org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler;
import team.unnamed.mocha.runtime.ExecutionContext;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.value.Function;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.NumberValue;
import team.unnamed.mocha.runtime.value.Value;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Owns the independent first/third-person Bedrock player-host runtimes for one renderer. */
public final class PlayerAnimationRuntime {
    private final ViewInstance firstPerson;
    private final ViewInstance thirdPerson;
    private final Map<String, AnimationDefinitions.AnimationData> onceAnimations = new LinkedHashMap<>();
    private final Map<String, Long> onceStartMillis = new LinkedHashMap<>();
    private final Vector3f animationScratch = new Vector3f();
    private final LongSupplier nowMillis;
    private final MovementDistance movementDistance = new MovementDistance();
    private McBoneModel cachedModel;

    public PlayerAnimationRuntime(PackManager packs, Map<String, String> animationOverrides) {
        this(packs, animationOverrides, System::currentTimeMillis);
    }

    PlayerAnimationRuntime(PackManager packs, Map<String, String> animationOverrides,
                           LongSupplier nowMillis) {
        Objects.requireNonNull(packs, "packs");
        this.nowMillis = Objects.requireNonNull(nowMillis, "nowMillis");
        final var definition = packs.getEntityDefinitions().getEntities().get("minecraft:player");
        if (definition == null) {
            throw new IllegalArgumentException("PackManager is missing minecraft:player");
        }
        this.firstPerson = new ViewInstance(definition.entityData(), animationOverrides, packs);
        this.thirdPerson = new ViewInstance(definition.entityData(), animationOverrides, packs);
    }

    public void sampleFirstPerson(PlayerModel model, PlayerAnimationState state) {
        sample(model, state, firstPerson);
    }

    public void sampleThirdPerson(PlayerModel model, PlayerAnimationState state) {
        sample(model, state, thirdPerson);
        org.oryxel.viabedrockutility.renderer.BedrockPlayerArmorPose.update(model);
    }

    void sampleFirstPerson(IBoneModel model, PlayerAnimationState state) {
        sample(model, state, firstPerson);
    }

    void sampleThirdPerson(IBoneModel model, PlayerAnimationState state) {
        sample(model, state, thirdPerson);
    }

    public void playOnce(String identifier, AnimationDefinitions.AnimationData animation) {
        if (animation == null || animation.compiled() == null) {
            return;
        }
        onceAnimations.put(identifier, animation);
        onceStartMillis.put(identifier, nowMillis.getAsLong());
    }

    private void sample(PlayerModel model, PlayerAnimationState state, ViewInstance view) {
        Objects.requireNonNull(state, "state");
        if (cachedModel == null || cachedModel.getModel() != model) {
            cachedModel = new McBoneModel(model);
        }
        clearVanillaHostRotations(model);
        sample(cachedModel, state, view);
    }

    private void sample(IBoneModel model, PlayerAnimationState state, ViewInstance view) {
        Objects.requireNonNull(state, "state");
        view.update(state, movementDistance.update(state));
        try {
            view.runtime.sample(model, state.partialTick(), view.scope, MoLangEvaluationContext.EMPTY);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to sample Bedrock player animation", exception);
        }
        applyOneShots(model, view.scope);
    }

    private void applyOneShots(IBoneModel model, Scope scope) {
        final long now = nowMillis.getAsLong();
        final Iterator<Map.Entry<String, AnimationDefinitions.AnimationData>> iterator =
                onceAnimations.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<String, AnimationDefinitions.AnimationData> entry = iterator.next();
            final long elapsed = now - onceStartMillis.getOrDefault(entry.getKey(), now);
            final float length = entry.getValue().compiled().lengthInSeconds();
            if (length > 0.0F && elapsed / 1000.0F >= length) {
                iterator.remove();
                onceStartMillis.remove(entry.getKey());
                continue;
            }
            AnimationHelper.animate(scope, model, entry.getValue().compiled(), elapsed,
                    1.0F, animationScratch, null);
        }
    }

    private static void clearVanillaHostRotations(PlayerModel model) {
        clear(model.head);
        clear(model.body);
        clear(model.rightArm);
        clear(model.leftArm);
        clear(model.rightLeg);
        clear(model.leftLeg);
    }

    private static void clear(ModelPart part) {
        part.xRot = 0.0F;
        part.yRot = 0.0F;
        part.zRot = 0.0F;
    }

    private static final class ViewInstance {
        private final LayeredScope scope = new LayeredScope(CustomEntityPayloadHandler.BASE_SCOPE);
        private final MutableObjectBinding query = new MutableObjectBinding();
        private final MutableObjectBinding variables = new MutableObjectBinding();
        private final EquippedItemNameValue equippedItemName = new EquippedItemNameValue();
        private final ClientEntityAnimationRuntime runtime;
        private PlayerAnimationState state;
        private ViewInstance(org.cube.converter.data.bedrock.BedrockEntityData entity,
                             Map<String, String> animationOverrides, PackManager packs) {
            scope.set("query", query);
            scope.set("q", query);
            scope.set("variable", variables);
            scope.set("v", variables);
            query.set("get_equipped_item_name", equippedItemName);
            query.set("is_item_name_any", (Function<Object>) this::isItemNameAny);
            query.set("equipped_item_any_tag", (Function<Object>) this::equippedItemAnyTag);
            query.set("property", (Function<Object>) (context, arguments) -> NumberValue.zero());
            query.setFunction("position_delta", this::positionDelta);
            query.setFunction("rotation_to_camera", ignored -> 0.0D);
            this.runtime = new ClientEntityAnimationRuntime(entity, animationOverrides, packs,
                    new AnimationClock.Client(), new AnimationEventListener() {
                        @Override
                        public void onTimelineEvent(List<String> expressions) {
                        }

                        @Override
                        public Scope getEntityScope() {
                            return scope;
                        }
                    });
        }

        private void update(PlayerAnimationState next, double distanceMoved) {
            this.state = next;
            equippedItemName.state = next;
            bindQueries(next, distanceMoved);
            bindVariables(next);
            runtime.tick(next.tick(), scope, MoLangEvaluationContext.EMPTY,
                    ignored -> bindVariables(next));
        }

        private void bindQueries(PlayerAnimationState value, double distanceMoved) {
            query.set("life_time", Value.of(value.ageInTicks() / 20.0F));
            query.set("frame_alpha", Value.of(value.partialTick()));
            query.set("modified_distance_moved", Value.of(distanceMoved));
            query.set("walk_distance", Value.of(distanceMoved));
            query.set("modified_move_speed", Value.of(value.walkSpeed()));
            query.set("ground_speed", Value.of(value.walkSpeed()));
            query.set("vertical_speed", Value.of(value.deltaY()));
            query.set("distance_from_camera", Value.of(value.distanceFromCamera()));
            query.set("model_scale", Value.of(0.0625D));
            query.set("is_alive", Value.of(value.alive()));
            query.set("is_on_ground", Value.of(value.onGround()));
            query.set("is_riding", Value.of(value.riding()));
            query.set("is_sneaking", Value.of(value.crouching()));
            query.set("is_sleeping", Value.of(value.sleeping()));
            query.set("is_swimming", Value.of(value.swimming()));
            query.set("is_gliding", Value.of(value.gliding()));
            query.set("is_baby", Value.of(value.baby()));
            query.set("is_spectator", Value.of(value.spectator()));
            query.set("blocking", Value.of(value.blocking()));
            query.set("is_charging", Value.of(value.charging()));
            query.set("get_animation_frame", Value.of(value.animationFrame()));
            query.set("item_is_charged", Value.of(value.itemCharged()));
            query.set("item_remaining_use_duration", Value.of(value.useRemainingTicks()));
            query.set("main_hand_item_use_duration", Value.of(value.useRemainingTicks()));
            query.set("main_hand_item_max_duration", Value.of(value.useMaxDuration()));
            query.set("has_target", NumberValue.zero());
            query.set("body_y_rotation", Value.of(value.bodyYaw()));
            query.set("body_x_rotation", Value.of(value.pitch()));
            query.set("target_x_rotation", Value.of(value.pitch()));
            query.set("target_y_rotation", Value.of(value.targetYRotation()));
            query.set("head_x_rotation", Value.of(value.pitch()));
            query.set("head_y_rotation", Value.of(value.targetYRotation()));
            query.set("day", Value.of(Math.max(1L, value.tick() / 24000L)));
        }

        private void bindVariables(PlayerAnimationState value) {
            variables.set("is_first_person", Value.of(value.view() == PlayerAnimationState.View.FIRST_PERSON));
            variables.set("is_paperdoll", NumberValue.zero());
            variables.set("map_face_icon", NumberValue.zero());
            variables.set("is_using_vr", NumberValue.zero());
            variables.set("is_vertical_splitscreen", NumberValue.zero());
            variables.set("is_horizontal_splitscreen", NumberValue.zero());
            final Value shortArmOffset = Value.of(value.slim() ? 0.5D : 0.0D);
            variables.set("short_arm_offset_right", shortArmOffset);
            variables.set("short_arm_offset_left", shortArmOffset);
            variables.set("player_arm_height", Value.of(value.armHeight()));
            variables.set("attack_time", Value.of(value.attackTime()));
            variables.set("player_x_rotation", Value.of(value.pitch()));
            variables.set("gliding_speed_value", Value.of(1.0D));
            variables.set("bob_animation", Value.of(value.bobAnimation()));
            variables.set("swim_amount", Value.of(value.swimAmount()));
            variables.set("is_holding_right", Value.of(isHoldingRight(value)));
            variables.set("is_holding_left", Value.of(isHoldingLeft(value)));
            variables.set("is_brandishing_spear", Value.of(value.brandishingSpear()));
            variables.set("damage_nearby_mobs", NumberValue.zero());
            variables.set("use_item_interval_progress", Value.of(value.useItemIntervalProgress()));
            variables.set("use_item_startup_progress", Value.of(value.useItemStartupProgress()));
        }

        private Value isItemNameAny(ExecutionContext<Object> context, Function.Arguments arguments) {
            if (state == null || arguments.length() < 2) {
                return NumberValue.zero();
            }
            final boolean offHand = isOffHand(arguments.next().eval().getAsString());
            final String equipped = offHand ? state.offHandIdentifier() : state.mainHandIdentifier();
            for (int i = 1; i < arguments.length(); i++) {
                if (equipped.equalsIgnoreCase(arguments.next().eval().getAsString())) {
                    return Value.of(true);
                }
            }
            return NumberValue.zero();
        }

        private Value equippedItemAnyTag(ExecutionContext<Object> context, Function.Arguments arguments) {
            if (state == null || arguments.length() < 2) {
                return NumberValue.zero();
            }
            final boolean offHand = isOffHand(arguments.next().eval().getAsString());
            if (offHand) {
                return NumberValue.zero();
            }
            for (int i = 1; i < arguments.length(); i++) {
                if (state.hasMainHandTag(arguments.next().eval().getAsString())) {
                    return Value.of(true);
                }
            }
            return NumberValue.zero();
        }

        private double positionDelta(double axis) {
            if (state == null) {
                return 0.0D;
            }
            return switch ((int) axis) {
                case 0 -> state.deltaX();
                case 1 -> state.deltaY();
                case 2 -> state.deltaZ();
                default -> 0.0D;
            };
        }

        private static boolean isHoldingRight(PlayerAnimationState state) {
            return state.mainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT
                    ? !state.mainHandIdentifier().isEmpty() : !state.offHandIdentifier().isEmpty();
        }

        private static boolean isHoldingLeft(PlayerAnimationState state) {
            return state.mainArm() == net.minecraft.world.entity.HumanoidArm.LEFT
                    ? !state.mainHandIdentifier().isEmpty() : !state.offHandIdentifier().isEmpty();
        }

        private static boolean isOffHand(String slot) {
            if (slot == null) {
                return false;
            }
            final String normalized = slot.toLowerCase(Locale.ROOT)
                    .replace("_", "").replace(".", "").replace("-", "");
            return normalized.contains("offhand");
        }
    }

    private static final class MovementDistance {
        private long tick = Long.MIN_VALUE;
        private float partialTick;
        private double x;
        private double z;
        private double distance;

        private double update(PlayerAnimationState state) {
            if (tick == Long.MIN_VALUE) {
                tick = state.tick();
                partialTick = state.partialTick();
                x = state.positionX();
                z = state.positionZ();
            } else if (state.tick() > tick
                    || state.tick() == tick && state.partialTick() > partialTick) {
                distance += Math.hypot(state.positionX() - x, state.positionZ() - z);
                tick = state.tick();
                partialTick = state.partialTick();
                x = state.positionX();
                z = state.positionZ();
            }
            return distance;
        }
    }

    private static final class EquippedItemNameValue implements Function<Object> {
        private PlayerAnimationState state;

        @Override
        public Value evaluate(ExecutionContext<Object> context, Arguments arguments) {
            final boolean offHand = arguments.length() > 0
                    && ViewInstance.isOffHand(arguments.next().eval().getAsString());
            return Value.of(state == null ? "" : state.equippedItemName(offHand));
        }

        @Override
        public boolean isString() {
            return true;
        }

        @Override
        public String getAsString() {
            return state == null ? "" : state.equippedItemName(false);
        }
    }
}
