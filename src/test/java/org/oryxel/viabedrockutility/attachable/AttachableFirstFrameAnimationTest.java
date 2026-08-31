package org.oryxel.viabedrockutility.attachable;

import net.easecation.bedrockmotion.pack.PackManager;
import net.easecation.bedrockmotion.pack.content.Content;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachableFirstFrameAnimationTest {

    @Test
    void replacementRuntimeAdvancesBeforeFirstRenderInTheSameTick() throws Exception {
        final Path gunPack = Path.of(System.getProperty("vbu.workspaceRoot"),
                "ec-deploy-assets", "bedrock-loader-packs", "ec_gun_r.zip");
        final PackManager packs = new PackManager(List.of(new Content(Files.readAllBytes(gunPack))));
        final String firstId = "easecation:gun_rifle_javelin_default";
        final String secondId = "easecation:gun_lmg_fury_default";
        final var firstDefinition = definition(packs, firstId);
        final var secondDefinition = definition(packs, secondId);
        final var registry = new AttachableRuntimeRegistry<AttachableRuntimeInstance>();
        final UUID ownerId = UUID.randomUUID();
        final var key = new AttachableRuntimeRegistry.RuntimeKey(ownerId,
                AttachableQueryContext.LogicalHand.MAIN_HAND);
        final long tick = 42L;

        final AttachableRuntimeInstance first = registry.getOrCreate(key,
                new AttachableRuntimeRegistry.RuntimeIdentity(firstId, firstDefinition.identifier(), 1L),
                tick, () -> new AttachableRuntimeInstance(firstDefinition, packs));
        update(first, ownerId, firstId, tick);
        assertTrue(first.advanceCurrentFrameTo(tick));

        final AttachableRuntimeInstance replacement = registry.getOrCreate(key,
                new AttachableRuntimeRegistry.RuntimeIdentity(secondId, secondDefinition.identifier(), 1L),
                tick, () -> new AttachableRuntimeInstance(secondDefinition, packs));
        assertNotSame(first, replacement);
        update(replacement, ownerId, secondId, tick);

        assertTrue(replacement.advanceCurrentFrameTo(tick));
        assertTrue(replacement.hasAnimator("animation.gun_lmg_fury_default.hold_first_person"));
        assertFalse(replacement.hasAnimator("animation.gun_lmg_fury_default.hold_third_person"));
        assertFalse(replacement.advanceCurrentFrameTo(tick));
    }

    private static net.easecation.bedrockmotion.pack.definitions.AttachableDefinitions.AttachableDefinition
    definition(PackManager packs, String identifier) {
        return packs.getAttachableDefinitions().candidatesFor(identifier).stream()
                .filter(candidate -> candidate.identifier().equals(identifier))
                .findFirst()
                .orElseThrow();
    }

    private static void update(AttachableRuntimeInstance runtime, UUID ownerId,
                               String itemIdentifier, long tick) {
        runtime.update(
                new AttachableOwnerSnapshot(ownerId, "minecraft:player", 0.0F, 0.0F, 0.0F),
                null,
                AttachableItemSnapshot.of(ResourceLocation.parse(itemIdentifier), new ItemStack(Items.STICK)),
                AttachableQueryContext.LogicalHand.MAIN_HAND,
                HumanoidArm.RIGHT,
                AttachableQueryContext.ViewContext.FIRST_PERSON,
                tick,
                0.5F
        );
    }
}
