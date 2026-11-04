package org.oryxel.viabedrockutility.animation;

import team.unnamed.mocha.runtime.Scope;
import net.easecation.bedrockmotion.mocha.DebugBinding;
import team.unnamed.mocha.runtime.ExecutionContext;
import team.unnamed.mocha.runtime.value.Function;
import team.unnamed.mocha.runtime.value.NumberValue;
import team.unnamed.mocha.runtime.value.Value;

import java.util.Locale;

/** The single source of native player queries for base controllers and owned pose layers. */
class PlayerAnimationInputs {
    final Scope scope = net.easecation.bedrockmotion.mocha.MoLangEngine.newScope();
    final DebugBinding query = new DebugBinding();
    final DebugBinding variables = new DebugBinding();
    private final EquippedItemNameValue equippedItemName = new EquippedItemNameValue();
    private org.oryxel.viabedrockutility.renderer.BedrockPlayerModelMetadata modelMetadata;
    PlayerAnimationState state;

    PlayerAnimationInputs() {
        scope.set("query", query);
        scope.set("q", query);
        scope.set("variable", variables);
        scope.set("v", variables);
        query.set("get_equipped_item_name", equippedItemName);
        query.set("is_item_name_any", (Function<Object>) this::isItemNameAny);
        query.set("equipped_item_any_tag", (Function<Object>) this::equippedItemAnyTag);
        query.set("get_default_bone_pivot", (Function<Object>) this::defaultBonePivot);
        query.set("property", (Function<Object>) (context, arguments) -> NumberValue.zero());
        query.setFunction("position_delta", this::positionDelta);
        query.setFunction("rotation_to_camera", ignored -> 0.0D);
    }

    void bindModel(net.minecraft.client.model.PlayerModel model) {
        modelMetadata = model == null ? null
                : org.oryxel.viabedrockutility.renderer.BedrockPlayerModelMetadata.get(model);
    }

    private Value defaultBonePivot(ExecutionContext<Object> context, Function.Arguments arguments) {
        if (modelMetadata == null || arguments.length() != 2) return NumberValue.zero();
        String name = arguments.next().eval().getAsString();
        double axis = arguments.next().eval().getAsNumber();
        if (!Double.isFinite(axis) || axis < 0 || axis > 2 || axis != Math.floor(axis)) return NumberValue.zero();
        return Value.of(modelMetadata.defaultBonePivot(name, (int) axis));
    }

    void update(PlayerAnimationState next, double distanceMoved) {
        state = next;
        equippedItemName.state = next;
        bindQueries(next, distanceMoved);
        bindVariables(next);
    }
    private void bindQueries(PlayerAnimationState value, double distanceMoved) {
        query.set("life_time", Value.of(value.ageInTicks() / 20.0F));
        query.set("frame_alpha", Value.of(value.partialTick()));
        query.set("modified_distance_moved", Value.of(value.walkPosition()));
        // 原生 walk_distance 查询对步行距离应用 0.6 的单位换算。
        query.set("walk_distance", Value.of(distanceMoved * 0.6D));
        query.set("modified_move_speed", Value.of(value.walkSpeed()));
        query.set("ground_speed", Value.of(value.groundSpeed()));
        query.set("vertical_speed", Value.of(value.verticalSpeed()));
        query.set("delta_time", Value.of(BedrockFrameTime.INSTANCE.deltaSeconds()));
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
        // has_target is an entity-target query, not the Java bow-use state. Some vanilla player
        // roots already apply bow_equipped while using a bow; setting this here would layer the
        // separate bow_and_arrow animation on top and double the arm offsets.
        query.set("has_target", NumberValue.zero());
        query.set("body_y_rotation", Value.of(value.bodyYaw()));
        query.set("body_x_rotation", Value.of(value.targetXRotation()));
        query.set("target_x_rotation", Value.of(value.targetXRotation()));
        query.set("target_y_rotation", Value.of(value.targetYRotation()));
        query.set("head_x_rotation", Value.of(value.targetXRotation()));
        query.set("head_y_rotation", Value.of(value.targetYRotation()));
        query.set("day", Value.of(Math.max(1L, value.tick() / 24000L)));
    }

    void bindVariables(PlayerAnimationState value) {
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
    private static final class EquippedItemNameValue implements Function<Object> {
        private PlayerAnimationState state;

        @Override
        public Value evaluate(ExecutionContext<Object> context, Arguments arguments) {
            final boolean offHand = arguments.length() > 0
                    && PlayerAnimationInputs.isOffHand(arguments.next().eval().getAsString());
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
