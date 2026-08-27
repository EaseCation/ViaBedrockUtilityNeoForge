package org.oryxel.viabedrockutility.animation;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerAnimationStateTest {
    @Test
    void bowAnimationFrameUsesJavaModelThresholds() {
        final ItemStack bow = new ItemStack(Items.BOW);

        assertEquals(0, PlayerAnimationState.animationFrame(
                bow, ItemUseAnimation.BOW, false, false, null, 20.0F, null));
        assertEquals(1, PlayerAnimationState.animationFrame(
                bow, ItemUseAnimation.BOW, true, false, null, 0.0F, null));
        assertEquals(2, PlayerAnimationState.animationFrame(
                bow, ItemUseAnimation.BOW, true, false, null, 13.0F, null));
        assertEquals(3, PlayerAnimationState.animationFrame(
                bow, ItemUseAnimation.BOW, true, false, null, 18.0F, null));
    }

    @Test
    void chargedCrossbowFramePreservesProjectileType() {
        final ItemStack arrowCrossbow = chargedCrossbow(Items.ARROW.getDefaultInstance());
        final ChargedProjectiles arrows = arrowCrossbow.get(DataComponents.CHARGED_PROJECTILES);
        assertEquals(4, PlayerAnimationState.animationFrame(
                arrowCrossbow, ItemUseAnimation.CROSSBOW, false, true, arrows, 0.0F, null));

        final ItemStack rocketCrossbow = chargedCrossbow(Items.FIREWORK_ROCKET.getDefaultInstance());
        final ChargedProjectiles rockets = rocketCrossbow.get(DataComponents.CHARGED_PROJECTILES);
        assertEquals(5, PlayerAnimationState.animationFrame(
                rocketCrossbow, ItemUseAnimation.CROSSBOW, false, true, rockets, 0.0F, null));
    }

    private static ItemStack chargedCrossbow(ItemStack projectile) {
        final ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(projectile));
        return crossbow;
    }
}
