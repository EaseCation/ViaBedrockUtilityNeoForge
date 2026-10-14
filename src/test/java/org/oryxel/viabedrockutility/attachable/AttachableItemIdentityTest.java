package org.oryxel.viabedrockutility.attachable;

import net.easecation.bedrockmotion.pack.PackManager;
import net.easecation.bedrockmotion.pack.content.Content;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachableItemIdentityTest {
    private static final String BAT_IDENTIFIER = "ec:stealegg_item_bat_baseball";
    private static PackManager packs;

    @BeforeAll
    static void loadAttachables() throws Exception {
        final Content content = new Content();
        // ec_stealegg_r 2.0.12 的球棒 attachable，保持与发布资源一致。
        try (var input = AttachableItemIdentityTest.class.getResourceAsStream(
                "/attachable-stealegg-bat/bat_baseball.attachable.json")) {
            assertNotNull(input);
            content.putString("attachables/bat_baseball.attachable.json",
                    new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        content.putString("attachables/paper.json", """
                {"format_version":"1.10.0","minecraft:attachable":{"description":{
                  "identifier":"test:paper_attachable","item":{"minecraft:paper":"1.0"}
                }}}
                """);
        content.putString("attachables/stick.json", """
                {"format_version":"1.10.0","minecraft:attachable":{"description":{
                  "identifier":"test:registered_attachable","item":{"minecraft:stick":"1.0"}
                }}}
                """);
        packs = new PackManager(List.of(content));
    }

    @Test
    void convertedPaperSelectsItsBedrockAttachableInBothViews() {
        // 来自 EggPC871A 的真实物品形态：paper 承载原始基岩标识和转换后的 item_model。
        final ItemStack bat = convertedPaper(BAT_IDENTIFIER);
        final ResourceLocation model = ResourceLocation.parse(
                "viabedrock:attachables/ec/stealegg_item_bat_baseball");
        bat.set(DataComponents.ITEM_MODEL, model);
        final AttachableItemSnapshot snapshot = manager().snapshotIfCandidate(bat);

        assertFalse(snapshot.isEmpty());
        assertEquals(ResourceLocation.parse(BAT_IDENTIFIER), snapshot.itemIdentifier());
        assertEquals(model, snapshot.stack().get(DataComponents.ITEM_MODEL));
        assertEquals(snapshot.itemIdentifier(), AttachableItemSnapshot.of(bat).itemIdentifier());
        for (var view : List.of(AttachableQueryContext.ViewContext.FIRST_PERSON,
                AttachableQueryContext.ViewContext.THIRD_PERSON)) {
            assertEquals(BAT_IDENTIFIER, candidate(snapshot, view).definition().identifier());
        }
    }

    @Test
    void registeredItemKeepsItsIdentityWithUnrelatedCustomData() {
        final ItemStack stick = new ItemStack(Items.STICK);
        final CompoundTag data = new CompoundTag();
        data.putString("owner", "identity-test");
        stick.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        final AttachableItemSnapshot snapshot = manager().snapshotIfCandidate(stick);

        assertEquals(ResourceLocation.parse("minecraft:stick"), snapshot.itemIdentifier());
        assertEquals(snapshot.itemIdentifier(), AttachableItemSnapshot.of(stick).itemIdentifier());
        assertEquals("test:registered_attachable", candidate(snapshot,
                AttachableQueryContext.ViewContext.THIRD_PERSON).definition().identifier());
    }

    @Test
    void unindexedBedrockItemDoesNotBorrowThePaperAttachable() {
        final ItemStack item = convertedPaper("test:unindexed");
        final AttachableRuntimeManager manager = manager();

        assertEquals(ResourceLocation.parse("minecraft:paper"),
                manager.snapshotIfCandidate(new ItemStack(Items.PAPER)).itemIdentifier());
        assertTrue(manager.snapshotIfCandidate(item).isEmpty());
        assertEquals(ResourceLocation.parse("test:unindexed"),
                AttachableItemSnapshot.of(item).itemIdentifier());
    }

    @Test
    void malformedBedrockIdentifierDoesNotSelectThePaperAttachable() {
        final ItemStack item = convertedPaper("invalid identifier");

        assertTrue(manager().snapshotIfCandidate(item).isEmpty());
        assertTrue(AttachableItemSnapshot.of(item).isEmpty());
    }

    private static ItemStack convertedPaper(String identifier) {
        final ItemStack stack = new ItemStack(Items.PAPER);
        final CompoundTag data = new CompoundTag();
        data.putString("viabedrock:bedrock_identifier", identifier);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    private static AttachableRuntimeManager manager() {
        final AttachableRuntimeManager manager = new AttachableRuntimeManager();
        manager.onPackManagerChanged(packs);
        return manager;
    }

    private static AttachableCandidateMatcher.Candidate candidate(AttachableItemSnapshot item,
                                                                  AttachableQueryContext.ViewContext view) {
        return AttachableCandidateMatcher.match(packs,
                new AttachableOwnerSnapshot(UUID.randomUUID(), "minecraft:player", 0.0F),
                null, item, AttachableQueryContext.LogicalHand.MAIN_HAND, HumanoidArm.RIGHT,
                view, 1L, 0.0F);
    }
}
