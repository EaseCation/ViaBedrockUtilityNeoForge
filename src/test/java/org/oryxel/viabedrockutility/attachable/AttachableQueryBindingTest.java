package org.oryxel.viabedrockutility.attachable;

import net.easecation.bedrockmotion.mocha.MoLangEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.value.Value;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachableQueryBindingTest {
    @Test
    void dottedProviderQueryResolvesThroughNamespace() throws Exception {
        var owner = UUID.randomUUID();
        var context = new AttachableQueryContext(owner, null, ItemStack.EMPTY,
                AttachableQueryContext.LogicalHand.MAIN_HAND, HumanoidArm.RIGHT,
                AttachableQueryContext.ViewContext.FIRST_PERSON, 12L, 0.5F, "test:gun");
        AttachableQueryProviders.register("query-binding-test", Set.of("mod.ecgun_fire"),
                (ignored, query) -> query.equals("mod.ecgun_fire")
                        ? Optional.of(AttachableQueryValue.number(1.0D)) : Optional.empty());

        try {
            assertTrue(AttachableQueryProviders.isNamespace("mod"));
            var scope = Scope.create();
            scope.set("query", new AttachableQueryBinding(context));
            assertEquals(1.0D, MoLangEngine.eval(scope, "query.mod.ecgun_fire").getAsNumber());
        } finally {
            AttachableQueryProviders.unregister("query-binding-test");
        }
    }

    @Test
    void stringValuedProviderQueryResolvesThroughBinding() throws Exception {
        var context = new AttachableQueryContext(UUID.randomUUID(), null, ItemStack.EMPTY,
                AttachableQueryContext.LogicalHand.MAIN_HAND, HumanoidArm.RIGHT,
                AttachableQueryContext.ViewContext.THIRD_PERSON, 1L, 0.0F, "test:gun");
        AttachableQueryProviders.register("query-string-test", Set.of("mod.owner_name"),
                (ignored, query) -> query.equals("mod.owner_name")
                        ? Optional.of(AttachableQueryValue.string("easecation")) : Optional.empty());

        try {
            var scope = Scope.create();
            scope.set("query", new AttachableQueryBinding(context));
            assertEquals("easecation", MoLangEngine.eval(scope, "query.mod.owner_name").getAsString());
        } finally {
            AttachableQueryProviders.unregister("query-string-test");
        }
    }

    @Test
    void ownerIdentifierIsBuiltInAsString() throws Exception {
        var owner = new AttachableOwnerSnapshot(
                UUID.randomUUID(), "minecraft:player", 0.0F, -12.5F, 37.0F);
        var item = new AttachableItemSnapshot(ResourceLocation.parse("minecraft:stick"), ItemStack.EMPTY);
        var frame = AttachableScopeFactory.RuntimeScope.temporary(owner, null, item,
                AttachableQueryContext.LogicalHand.MAIN_HAND, HumanoidArm.RIGHT,
                AttachableQueryContext.ViewContext.THIRD_PERSON, 1L, 0.0F, "test:attachable");

        var value = MoLangEngine.eval(frame.scope(), frame.context(), "query.owner_identifier");
        assertEquals("minecraft:player", value.getAsString());
        assertEquals(-12.5D, MoLangEngine.eval(frame.scope(), frame.context(),
                "query.target_x_rotation").getAsNumber());
        assertEquals(37.0D, MoLangEngine.eval(frame.scope(), frame.context(),
                "query.target_y_rotation").getAsNumber());
    }

    @Test
    void invisibleOwnerIsExposedToBedrockQueries() throws Exception {
        final ArmorStand ownerEntity = new ArmorStand(EntityType.ARMOR_STAND, null);
        ownerEntity.setInvisible(true);
        final AttachableOwnerSnapshot owner = new AttachableOwnerSnapshot(
                UUID.randomUUID(), "minecraft:player", 0.0F);
        final AttachableItemSnapshot item = new AttachableItemSnapshot(
                ResourceLocation.parse("minecraft:wooden_sword"), ItemStack.EMPTY);
        final var frame = AttachableScopeFactory.RuntimeScope.temporary(owner, ownerEntity, item,
                AttachableQueryContext.LogicalHand.MAIN_HAND, HumanoidArm.RIGHT,
                AttachableQueryContext.ViewContext.THIRD_PERSON, 1L, 0.0F, "minecraft:wooden_sword.player");

        assertEquals(1.0D, MoLangEngine.eval(frame.scope(), frame.context(),
                "query.is_invisible").getAsNumber());

        ownerEntity.setInvisible(false);
        final var visibleFrame = AttachableScopeFactory.RuntimeScope.temporary(owner, ownerEntity, item,
                AttachableQueryContext.LogicalHand.MAIN_HAND, HumanoidArm.RIGHT,
                AttachableQueryContext.ViewContext.THIRD_PERSON, 2L, 0.0F, "minecraft:wooden_sword.player");
        assertEquals(0.0D, MoLangEngine.eval(visibleFrame.scope(), visibleFrame.context(),
                "query.is_invisible").getAsNumber());
    }

    @Test
    void itemSlotToBoneNameUsesRequestedLogicalHand() throws Exception {
        var owner = new AttachableOwnerSnapshot(UUID.randomUUID(), "minecraft:player", 0.0F);
        var item = new AttachableItemSnapshot(ResourceLocation.parse("minecraft:stick"), ItemStack.EMPTY);
        var mainFrame = AttachableScopeFactory.RuntimeScope.temporary(owner, null, item,
                AttachableQueryContext.LogicalHand.MAIN_HAND, HumanoidArm.RIGHT,
                AttachableQueryContext.ViewContext.FIRST_PERSON, 1L, 0.0F, "test:attachable");

        assertEquals("rightitem", MoLangEngine.eval(mainFrame.scope(), mainFrame.context(),
                "query.item_slot_to_bone_name('main_hand')").getAsString());
        assertEquals("leftitem", MoLangEngine.eval(mainFrame.scope(), mainFrame.context(),
                "query.item_slot_to_bone_name('off_hand')").getAsString());

        var leftOffhandFrame = AttachableScopeFactory.RuntimeScope.temporary(owner, null, item,
                AttachableQueryContext.LogicalHand.OFF_HAND, HumanoidArm.LEFT,
                AttachableQueryContext.ViewContext.THIRD_PERSON, 1L, 0.0F, "test:attachable");
        assertEquals("rightitem", MoLangEngine.eval(leftOffhandFrame.scope(), leftOffhandFrame.context(),
                "query.item_slot_to_bone_name('main_hand')").getAsString());
        assertEquals("leftitem", MoLangEngine.eval(leftOffhandFrame.scope(), leftOffhandFrame.context(),
                "query.item_slot_to_bone_name(context.item_slot)").getAsString());
    }

    @Test
    void failingProviderDoesNotBlockLaterProvider() {
        var context = new AttachableQueryContext(UUID.randomUUID(), null, ItemStack.EMPTY,
                AttachableQueryContext.LogicalHand.MAIN_HAND, HumanoidArm.RIGHT,
                AttachableQueryContext.ViewContext.THIRD_PERSON, 1L, 0.0F, "test:provider-isolation");
        AttachableQueryProviders.register("query-throwing-test", Set.of("mod.shared"),
                (ignored, query) -> {
                    throw new IllegalStateException("expected test failure");
                });
        AttachableQueryProviders.register("query-fallback-test", Set.of("mod.shared"),
                (ignored, query) -> query.equals("mod.shared")
                        ? Optional.of(AttachableQueryValue.number(7.0D)) : Optional.empty());

        try {
            assertEquals(7.0D, AttachableQueryProviders.resolveIfHandled(context, "mod.shared")
                    .orElseThrow().number());
        } finally {
            AttachableQueryProviders.unregister("query-throwing-test");
            AttachableQueryProviders.unregister("query-fallback-test");
        }
    }

    @Test
    void unknownQueryWarnsOnlyOnce() {
        var context = new AttachableQueryContext(UUID.randomUUID(), null, ItemStack.EMPTY,
                AttachableQueryContext.LogicalHand.OFF_HAND, HumanoidArm.LEFT,
                AttachableQueryContext.ViewContext.THIRD_PERSON, 0L, 0.0F, "test:warn-once");
        final int before = AttachableQueryProviders.warnedUnknownCount();
        assertTrue(AttachableQueryProviders.resolve(context,
                "definitely_unknown_query_x", "query.definitely_unknown_query_x").isEmpty());
        assertTrue(AttachableQueryProviders.resolve(context,
                "definitely_unknown_query_x", "query.definitely_unknown_query_x").isEmpty());
        assertEquals(before + 1, AttachableQueryProviders.warnedUnknownCount());
    }
}
