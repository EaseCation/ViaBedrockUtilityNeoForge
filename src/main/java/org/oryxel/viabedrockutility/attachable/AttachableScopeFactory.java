package org.oryxel.viabedrockutility.attachable;

import net.easecation.bedrockmotion.mocha.MoLangEvaluationContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.value.Function;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.NumberValue;
import team.unnamed.mocha.runtime.value.Value;

import java.util.Locale;
import java.util.Map;

/** MoLang actor and query-scope binding for attachable condition/animation evaluation. */
final class AttachableScopeFactory {

    private AttachableScopeFactory() {
    }

    static final class RuntimeActor implements MoLangEvaluationContext.Actor {
        final MutableObjectBinding variables;

        RuntimeActor(MutableObjectBinding variables) {
            this.variables = variables;
        }

        @Override
        public Value variable(String normalizedName) {
            return variables.get(normalizedName);
        }
    }

    record OwnerActor(AttachableOwnerSnapshot owner) implements MoLangEvaluationContext.Actor {
        @Override
        public Value variable(String normalizedName) {
            return normalizedName.equalsIgnoreCase("attack_time")
                    ? Value.of(owner.attackTime()) : NumberValue.zero();
        }
    }

    record RuntimeScope(Scope scope, MoLangEvaluationContext context,
                        AttachableQueryContext.LogicalHand hand, HumanoidArm arm,
                        AttachableQueryContext.ViewContext view, long tick) {
        static RuntimeScope temporary(AttachableOwnerSnapshot owner, Entity ownerEntity,
                                      AttachableItemSnapshot item,
                                      AttachableQueryContext.LogicalHand hand, HumanoidArm arm,
                                      AttachableQueryContext.ViewContext view, long tick, float partialTick,
                                      String attachable) {
            final MutableObjectBinding variables = new MutableObjectBinding();
            final Scope scope = Scope.create();
            scope.set("variable", variables);
            scope.set("v", variables);
            return create(scope, new RuntimeActor(variables), owner, ownerEntity, item, hand, arm, view,
                    tick, partialTick, attachable);
        }

        static RuntimeScope persistent(Scope scope, RuntimeActor actor,
                                       AttachableOwnerSnapshot owner, Entity ownerEntity,
                                       AttachableItemSnapshot item,
                                       AttachableQueryContext.LogicalHand hand, HumanoidArm arm,
                                       AttachableQueryContext.ViewContext view, long tick, float partialTick,
                                       String attachable) {
            return create(scope, actor, owner, ownerEntity, item, hand, arm, view, tick, partialTick, attachable);
        }

        private static RuntimeScope create(Scope scope, RuntimeActor actor,
                                           AttachableOwnerSnapshot owner, Entity ownerEntity,
                                           AttachableItemSnapshot item,
                                           AttachableQueryContext.LogicalHand hand, HumanoidArm arm,
                                           AttachableQueryContext.ViewContext view, long tick, float partialTick,
                                           String attachable) {
            final OwnerActor ownerActor = new OwnerActor(owner);
            final AttachableQueryContext queryContext = new AttachableQueryContext(owner.uuid(), ownerEntity,
                    item.stack(), hand, arm, view, tick, partialTick, attachable);
            final MoLangEvaluationContext evaluationContext = new MoLangEvaluationContext(
                    actor, ownerActor, item.stack(), hand.name().toLowerCase(Locale.ROOT), Map.of(), view.name());
            final MutableObjectBinding contextBinding = new MutableObjectBinding();
            contextBinding.set("owning_entity", evaluationContext.owningEntityValue());
            contextBinding.set("is_first_person", Value.of(view == AttachableQueryContext.ViewContext.FIRST_PERSON));
            contextBinding.set("item_slot", Value.of(hand == AttachableQueryContext.LogicalHand.MAIN_HAND
                    ? "main_hand" : "off_hand"));
            scope.set("context", contextBinding);
            scope.set("c", contextBinding);

            final AttachableQueryBinding query = new AttachableQueryBinding(queryContext);
            query.set("model_scale", Value.of(1.0D));
            query.set("variant", NumberValue.zero());
            query.set("owner_identifier", Value.of(owner.identifier() == null ? "" : owner.identifier()));
            query.set("item_slot_to_bone_name", stringFunction((execution, arguments) -> {
                String slot = hand == AttachableQueryContext.LogicalHand.MAIN_HAND
                        ? "main_hand" : "off_hand";
                if (arguments.length() > 0) {
                    slot = normalizeSlot(arguments.next().eval().getAsString());
                }
                final AttachableQueryContext.LogicalHand requestedHand =
                        slot.equals("off_hand")
                                ? AttachableQueryContext.LogicalHand.OFF_HAND
                                : AttachableQueryContext.LogicalHand.MAIN_HAND;
                final HumanoidArm requestedArm = requestedHand == hand ? arm : arm.getOpposite();
                        return Value.of(requestedArm == HumanoidArm.RIGHT ? "rightitem" : "leftitem");
            }));
            query.set("is_invisible", Value.of(queryContext.owner() != null
                    && queryContext.owner().isInvisible()));
            query.set("is_spectator", Value.of(queryContext.owner() instanceof net.minecraft.world.entity.player.Player player
                    && player.isSpectator()));
            query.set("owner_y_rotation", Value.of(queryContext.owner() == null
                    ? 0.0F : queryContext.owner().getYRot(queryContext.partialTick())));
            query.set("owner_x_rotation", Value.of(queryContext.owner() == null
                    ? 0.0F : queryContext.owner().getXRot(queryContext.partialTick())));
            query.set("target_x_rotation", Value.of(owner.targetXRotation()));
            query.set("target_y_rotation", Value.of(owner.targetYRotation()));
            query.set("head_x_rotation", Value.of(owner.targetXRotation()));
            query.set("head_y_rotation", Value.of(owner.targetYRotation()));
            query.set("is_owner_identifier_any", stringFunction((execution, arguments) ->
                    Value.of(anyEquals(arguments, owner.identifier()))));
            query.set("is_item_name_any", stringFunction((execution, arguments) ->
                    Value.of(anyEquals(arguments, item.itemIdentifier().toString()))));
            query.set("property", stringFunction((execution, arguments) -> {
                if (arguments.length() == 0) {
                    return NumberValue.zero();
                }
                final String propertyName = arguments.next().eval().getAsString();
                return AttachableQueryProviders.resolveIfHandled(queryContext, "property." + propertyName)
                        .map(AttachableScopeFactory::toMocha)
                        .orElse(NumberValue.zero());
            }));
            scope.set("query", query);
            scope.set("q", query);
            return new RuntimeScope(scope, evaluationContext, hand, arm, view, tick);
        }

        private static Function<Object> stringFunction(Function<Object> function) {
            return function;
        }

        private static boolean anyEquals(Function.Arguments arguments, String expected) {
            for (int i = 0; i < arguments.length(); i++) {
                if (expected.equalsIgnoreCase(arguments.next().eval().getAsString())) {
                    return true;
                }
            }
            return false;
        }

        private static String normalizeSlot(String value) {
            if (value == null) {
                return "main_hand";
            }
            final String normalized = value.toLowerCase(Locale.ROOT)
                    .replace("-", "").replace("_", "").replace(" ", "");
            return normalized.equals("offhand") || normalized.equals("off")
                    ? "off_hand" : "main_hand";
        }
    }

    private static Value toMocha(AttachableQueryValue value) {
        return switch (value.kind()) {
            case NUMBER -> Value.of(value.number());
            case BOOLEAN -> Value.of(value.booleanValue() ? 1.0D : 0.0D);
            case STRING -> Value.of(value.stringValue());
        };
    }
}
