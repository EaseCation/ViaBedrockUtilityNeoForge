package org.oryxel.viabedrockutility.animation;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerAnimationStateTest {
    @Test
    void firstPersonUsesBedrockLogicalHandsWhileThirdPersonKeepsJavaHandedness() {
        assertEquals(HumanoidArm.RIGHT, PlayerAnimationState.bedrockMainArm(
                PlayerAnimationState.View.FIRST_PERSON, HumanoidArm.LEFT));
        assertEquals(HumanoidArm.RIGHT, PlayerAnimationState.bedrockMainArm(
                PlayerAnimationState.View.FIRST_PERSON, HumanoidArm.RIGHT));
        assertEquals(HumanoidArm.LEFT, PlayerAnimationState.bedrockMainArm(
                PlayerAnimationState.View.THIRD_PERSON, HumanoidArm.LEFT));
    }

    @Test
    void firstPersonTargetLookStaysInCameraSpace() {
        final PlayerAnimationState firstPerson = state(
                PlayerAnimationState.View.FIRST_PERSON, 60.0F, 75.0F);
        assertEquals(0.0F, firstPerson.targetXRotation());
        assertEquals(0.0F, firstPerson.targetYRotation());

        final PlayerAnimationState thirdPerson = state(
                PlayerAnimationState.View.THIRD_PERSON, 60.0F, 75.0F);
        assertEquals(60.0F, thirdPerson.targetXRotation());
        assertEquals(75.0F, thirdPerson.targetYRotation());
    }

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

    private static PlayerAnimationState state(PlayerAnimationState.View view, float pitch,
                                              float relativeHeadYaw) {
        return new PlayerAnimationState(
                java.util.UUID.randomUUID(), new PlayerAnimationOwner(new Object(), new Object()),
                view, 0L, 0.0F, HumanoidArm.RIGHT,
                net.minecraft.world.InteractionHand.MAIN_HAND, "", "", java.util.Set.of(),
                0.0F, 0.0F, 0.0F, 0.0F, pitch, relativeHeadYaw, 0.0F, 0.0F,
                0.0F, 1.0F, false, true, true, false, false, false, false, false,
                false, false, false, false, false, false, false, false,
                0, 0, 0, 0.0F, 0.0F,
                0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
    }
}
