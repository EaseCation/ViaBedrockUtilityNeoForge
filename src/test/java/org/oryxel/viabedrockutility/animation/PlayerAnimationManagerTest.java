package org.oryxel.viabedrockutility.animation;

import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerAnimationManagerTest {
    @Test
    void ridingSlotsOnlyRunWhilePassenger() {
        final PlayerRenderState state = new PlayerRenderState();

        assertFalse(PlayerAnimationManager.isAnimationActive("riding.arms", state));
        assertFalse(PlayerAnimationManager.isAnimationActive("RIDING.LEGS", state));
        assertTrue(PlayerAnimationManager.isAnimationActive("move.arms", state));
        assertTrue(PlayerAnimationManager.isAnimationActive("holding", state));

        state.isPassenger = true;
        assertTrue(PlayerAnimationManager.isAnimationActive("riding.arms", state));
        assertTrue(PlayerAnimationManager.isAnimationActive("riding.legs", state));
    }

    @Test
    void playerPreAnimationProducesBedrockWalkSwing() {
        assertEquals(0.0D, PlayerAnimationManager.computeTcos0(12.0F, 0.0F), 1.0E-6D);
        assertEquals(57.3D, PlayerAnimationManager.computeTcos0(0.0F, 1.0F), 1.0E-6D);
        assertEquals(-57.3D,
                PlayerAnimationManager.computeTcos0((float) (180.0D / 38.17D), 1.0F),
                1.0E-4D);
    }

    @Test
    void playerPreAnimationProducesBedrockAttackBodyYaw() {
        assertEquals(0.0D, PlayerAnimationManager.computeAttackBodyYaw(0.0F), 1.0E-6D);
        assertEquals(5.0D, PlayerAnimationManager.computeAttackBodyYaw(0.0625F), 1.0E-6D);
    }

    @Test
    void itemNameQueryUsesTheRequestedBedrockEquipmentSlot() {
        final List<String> guns = List.of("0", "easecation:gun_rifle_dynamic_default");

        assertTrue(PlayerAnimationManager.matchesItemNameAny(
                "slot.weapon.mainhand", "easecation:gun_rifle_dynamic_default", "minecraft:air", guns));
        assertFalse(PlayerAnimationManager.matchesItemNameAny(
                "slot.weapon.offhand", "easecation:gun_rifle_dynamic_default", "minecraft:air", guns));
        assertTrue(PlayerAnimationManager.matchesItemNameAny(
                "SLOT.WEAPON.OFF_HAND", "minecraft:air", "easecation:gun_rifle_dynamic_default", guns));
    }

    @Test
    void bedrockTargetYawUsesJavaHeadYawThatIsAlreadyBodyRelative() {
        assertEquals(18.0F, PlayerAnimationManager.bedrockTargetYaw(18.0F), 1.0E-6F);
    }
}
