package org.oryxel.viabedrockutility.attachable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import org.oryxel.viabedrockutility.renderer.BedrockPlayerModelMetadata;
import org.oryxel.viabedrockutility.renderer.CustomPlayerRenderer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FirstPersonAttachableRenderer {
    private static final ThreadLocal<Boolean> RENDERING = ThreadLocal.withInitial(() -> false);
    private static final Set<String> DIAGNOSED = ConcurrentHashMap.newKeySet();

    private FirstPersonAttachableRenderer() {
    }

    public static void onRenderHand(RenderHandEvent event) {
        if (RENDERING.get()) {
            return;
        }
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        final var renderer = ViaBedrockUtility.getInstance().getPayloadHandler()
                .getCachedPlayerRenderers().get(player.getUUID());
        final AttachableItemSnapshot item = ViaBedrockUtility.getInstance()
                .getAttachableRuntimeManager().snapshotIfCandidate(event.getItemStack());
        if (!(renderer instanceof CustomPlayerRenderer customRenderer)) {
            if (!item.isEmpty() && DIAGNOSED.add("renderer:" + item.itemIdentifier())) {
                ViaBedrockUtilityNeoForge.LOGGER.debug(
                        "[Attachable] First-person event for {}, item={}, itemClass={}, customRenderer={}",
                        player.getUUID(), item.itemIdentifier(), event.getItemStack().getItem().getClass().getName(),
                        renderer == null ? "missing" : renderer.getClass().getName());
            }
            return;
        }

        final AttachableQueryContext.LogicalHand logicalHand = event.getHand() == InteractionHand.MAIN_HAND
                ? AttachableQueryContext.LogicalHand.MAIN_HAND
                : AttachableQueryContext.LogicalHand.OFF_HAND;
        final HumanoidArm arm = logicalHand == AttachableQueryContext.LogicalHand.MAIN_HAND
                ? player.getMainArm() : player.getMainArm().getOpposite();
        final float partialTick = event.getPartialTick();
        final float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        final AttachableOwnerSnapshot owner = new AttachableOwnerSnapshot(
                player.getUUID(), "minecraft:player", player.getAttackAnim(partialTick),
                player.getXRot(partialTick), Mth.wrapDegrees(player.getYRot(partialTick) - bodyYaw));

        if (!item.isEmpty() && DIAGNOSED.add("entry:" + item.itemIdentifier())) {
            ViaBedrockUtilityNeoForge.LOGGER.debug(
                    "[Attachable] First-person event reached runtime manager: owner={}, item={}, itemClass={}, metadata={}",
                    player.getUUID(), item.itemIdentifier(), event.getItemStack().getItem().getClass().getName(),
                    BedrockPlayerModelMetadata.get(customRenderer.getPlayerModel()) != null);
        }

        RENDERING.set(true);
        try {
            final AttachableRenderResult result = ViaBedrockUtility.getInstance().getAttachableRuntimeManager().renderFirstPerson(
                    owner, item, logicalHand, arm,
                    customRenderer.getPlayerModel(), event.getPoseStack(), event.getMultiBufferSource(),
                    event.getPackedLight(), event.getPartialTick(), () -> renderArm(event, customRenderer, arm));
            if (result == AttachableRenderResult.NOT_APPLICABLE) {
                // Bedrock's empty-hand first-person arm is a separate camera-space pose. It must not
                // inherit either Java's ItemInHandRenderer transform or the third-person zombie pose.
                // Only the main-hand empty event renders an arm in vanilla; preserving that rule avoids
                // introducing a second empty off-hand arm. Attachable items retain priority above.
                if (logicalHand == AttachableQueryContext.LogicalHand.MAIN_HAND
                        && event.getItemStack().isEmpty() && !player.isInvisible()
                        && renderBedrockArm(event, customRenderer, arm, event.getSwingProgress())) {
                    event.setCanceled(true);
                }
                return;
            }
            event.setCanceled(true);
        } finally {
            RENDERING.set(false);
        }
    }

    private static void renderArm(RenderHandEvent event, CustomPlayerRenderer renderer, HumanoidArm arm) {
        if (renderBedrockArm(event, renderer, arm)) {
            return;
        }

        // A malformed custom host should still retain a hand when the attachable takes over the event.
        if (DIAGNOSED.add("arm-fallback:" + arm)) {
            ViaBedrockUtilityNeoForge.LOGGER.warn(
                    "[Attachable] Missing VBU {} arm metadata; using the inherited first-person arm fallback",
                    arm.name().toLowerCase(java.util.Locale.ROOT));
        }
        if (arm == HumanoidArm.RIGHT) {
            renderer.renderRightHand(event.getPoseStack(), event.getMultiBufferSource(),
                    event.getPackedLight(), renderer.getPlayerTexture(), true,
                    Minecraft.getInstance().player);
        } else {
            renderer.renderLeftHand(event.getPoseStack(), event.getMultiBufferSource(),
                    event.getPackedLight(), renderer.getPlayerTexture(), true,
                    Minecraft.getInstance().player);
        }
    }

    private static boolean renderBedrockArm(RenderHandEvent event, CustomPlayerRenderer renderer, HumanoidArm arm) {
        return renderBedrockArm(event, renderer, arm, 0.0F);
    }

    private static boolean renderBedrockArm(RenderHandEvent event, CustomPlayerRenderer renderer,
                                            HumanoidArm arm, float attackTime) {
        final BedrockPlayerModelMetadata metadata = BedrockPlayerModelMetadata.get(renderer.getPlayerModel());
        if (metadata != null && FirstPersonBedrockArmRenderer.render(
                new AttachableHostContext(metadata), arm, renderer.getPlayerTexture(),
                event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), attackTime)) {
            return true;
        }
        return false;
    }
}
