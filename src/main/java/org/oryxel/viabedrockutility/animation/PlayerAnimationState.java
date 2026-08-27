package org.oryxel.viabedrockutility.animation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Immutable Minecraft-side input sampled once for one player animation evaluation. */
public record PlayerAnimationState(
        UUID playerUuid,
        View view,
        long tick,
        float partialTick,
        HumanoidArm mainArm,
        InteractionHand usedHand,
        String mainHandIdentifier,
        String offHandIdentifier,
        Set<String> mainHandTags,
        float ageInTicks,
        float walkPosition,
        float walkSpeed,
        float attackTime,
        float pitch,
        float relativeHeadYaw,
        float bodyYaw,
        float swimAmount,
        float distanceFromCamera,
        boolean alive,
        boolean onGround,
        boolean riding,
        boolean crouching,
        boolean sleeping,
        boolean swimming,
        boolean gliding,
        boolean baby,
        boolean spectator,
        boolean usingItem,
        boolean blocking,
        boolean charging,
        boolean brandishingSpear,
        boolean itemCharged,
        boolean bobAnimation,
        int animationFrame,
        int useRemainingTicks,
        int useMaxDuration,
        float useItemStartupProgress,
        float useItemIntervalProgress,
        double deltaX,
        double deltaY,
        double deltaZ) {

    public static PlayerAnimationState thirdPerson(AbstractClientPlayer player,
                                                   PlayerRenderState renderState,
                                                   float partialTick) {
        return capture(player, renderState, View.THIRD_PERSON, partialTick);
    }

    public static PlayerAnimationState firstPerson(AbstractClientPlayer player, float partialTick) {
        return capture(player, null, View.FIRST_PERSON, partialTick);
    }

    private static PlayerAnimationState capture(AbstractClientPlayer player,
                                                PlayerRenderState renderState,
                                                View view,
                                                float partialTick) {
        final ItemStack mainHand = player.getMainHandItem();
        final ItemStack offHand = player.getOffhandItem();
        final boolean usingMainHand = player.isUsingItem()
                && player.getUsedItemHand() == InteractionHand.MAIN_HAND
                && !mainHand.isEmpty();
        final ItemUseAnimation useAnimation = usingMainHand
                ? mainHand.getUseAnimation() : ItemUseAnimation.NONE;
        final int useRemainingTicks = usingMainHand ? player.getUseItemRemainingTicks() : 0;
        final int useMaxDuration = usingMainHand ? mainHand.getUseDuration(player) : 0;
        final float useElapsedTicks = usingMainHand
                ? Math.max(0.0F, useMaxDuration - useRemainingTicks + partialTick - 1.0F) : 0.0F;
        final ChargedProjectiles projectiles = mainHand.get(DataComponents.CHARGED_PROJECTILES);
        final boolean itemCharged = projectiles != null && !projectiles.isEmpty();
        final Vec3 movement = player.getDeltaMovement();
        final float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        final float headYaw = Mth.wrapDegrees(Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot)
                - bodyYaw);
        final float pitch = player.getXRot(partialTick);
        final float walkPosition = renderState == null
                ? player.walkAnimation.position(partialTick) : renderState.walkAnimationPos;
        final float walkSpeed = renderState == null
                ? player.walkAnimation.speed(partialTick) : renderState.walkAnimationSpeed;
        final float attackTime = renderState == null
                ? player.getAttackAnim(partialTick) : renderState.attackTime;
        final float swimAmount = renderState == null
                ? player.getSwimAmount(partialTick) : renderState.swimAmount;
        final double distance = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .distanceTo(player.getPosition(partialTick));

        return new PlayerAnimationState(
                player.getUUID(), view, player.tickCount, partialTick, player.getMainArm(),
                player.isUsingItem() ? player.getUsedItemHand() : InteractionHand.MAIN_HAND,
                identifier(mainHand), identifier(offHand), tags(mainHand),
                player.tickCount + partialTick, walkPosition, walkSpeed, attackTime,
                pitch, headYaw, bodyYaw, swimAmount, (float) distance,
                player.isAlive(), player.onGround(), player.isPassenger(), player.isCrouching(),
                player.isSleeping(), player.isVisuallySwimming(), player.isFallFlying(),
                player.isBaby(), player.isSpectator(), player.isUsingItem(), player.isBlocking(),
                usingMainHand && useAnimation == ItemUseAnimation.CROSSBOW,
                usingMainHand && useAnimation == ItemUseAnimation.SPEAR, itemCharged,
                Minecraft.getInstance().options.bobView().get(),
                animationFrame(mainHand, useAnimation, usingMainHand, itemCharged,
                        projectiles, useElapsedTicks, player),
                useRemainingTicks, useMaxDuration,
                usingMainHand ? Mth.clamp(useElapsedTicks / 5.0F, 0.0F, 1.0F) : 0.0F,
                useItemIntervalProgress(useAnimation, useElapsedTicks),
                movement.x, movement.y, movement.z);
    }

    public String equippedItemName(boolean offHand) {
        final String identifier = offHand ? offHandIdentifier : mainHandIdentifier;
        final int separator = identifier.indexOf(':');
        return separator < 0 ? identifier : identifier.substring(separator + 1);
    }

    public boolean hasMainHandTag(String identifier) {
        final String normalized = identifier == null ? "" : identifier.toLowerCase(Locale.ROOT);
        return mainHandTags.contains(normalized);
    }

    private static String identifier(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        final ResourceLocation identifier = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return identifier == null ? "" : identifier.toString();
    }

    private static Set<String> tags(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Set.of();
        }
        return stack.getTags()
                .map(tag -> tag.location().toString().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    static int animationFrame(ItemStack stack,
                              ItemUseAnimation useAnimation,
                              boolean usingMainHand,
                              boolean itemCharged,
                              ChargedProjectiles projectiles,
                              float useElapsedTicks,
                              AbstractClientPlayer player) {
        if (useAnimation == ItemUseAnimation.CROSSBOW && itemCharged) {
            return projectiles != null && projectiles.contains(Items.FIREWORK_ROCKET) ? 5 : 4;
        }
        if (!usingMainHand) {
            return 0;
        }
        if (useAnimation == ItemUseAnimation.BOW) {
            return useElapsedTicks >= 18.0F ? 3 : useElapsedTicks >= 13.0F ? 2 : 1;
        }
        if (useAnimation == ItemUseAnimation.CROSSBOW && stack.getItem() instanceof CrossbowItem) {
            final float pull = useElapsedTicks / CrossbowItem.getChargeDuration(stack, player);
            return pull >= 1.0F ? 3 : pull >= 0.58F ? 2 : 1;
        }
        return 0;
    }

    private static float useItemIntervalProgress(ItemUseAnimation useAnimation, float useElapsedTicks) {
        if (useAnimation != ItemUseAnimation.EAT && useAnimation != ItemUseAnimation.DRINK) {
            return 0.0F;
        }
        return (1.0F - Mth.cos(useElapsedTicks * Mth.PI / 4.0F)) * 0.5F;
    }

    public enum View {
        FIRST_PERSON,
        THIRD_PERSON
    }
}
