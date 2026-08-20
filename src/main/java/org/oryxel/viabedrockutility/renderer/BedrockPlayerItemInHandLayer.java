package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.attachable.AttachableItemSnapshot;
import org.oryxel.viabedrockutility.attachable.AttachableQueryContext;
import org.oryxel.viabedrockutility.attachable.AttachableRenderResult;
import org.oryxel.viabedrockutility.mixin.interfaces.ICustomPlayerRendererHolder;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BedrockPlayerItemInHandLayer extends PlayerItemInHandLayer<PlayerRenderState, PlayerModel> {
    private static final float JAVA_HAND_X_OFFSET = 1.0F / 16.0F;
    private static final float JAVA_HAND_Y_OFFSET = 0.125F;
    private static final float JAVA_HAND_Z_OFFSET = -0.625F;
    private static boolean warnedMissingMetadata;
    private static boolean warnedMissingAttach;
    private static final Set<String> loggedAttachPoints = ConcurrentHashMap.newKeySet();

    public BedrockPlayerItemInHandLayer(CustomPlayerRenderer renderer) {
        super(renderer);
    }

    @Override
    protected void renderArmWithItem(PlayerRenderState state, ItemStackRenderState item, HumanoidArm arm,
                                     PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        final InteractionHand hand = arm == state.mainArm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        final ICustomPlayerRendererHolder renderState = (ICustomPlayerRendererHolder) state;
        final AttachableItemSnapshot snapshot = hand == InteractionHand.MAIN_HAND
                ? renderState.viaBedrockUtility$getMainHandSnapshot()
                : renderState.viaBedrockUtility$getOffHandSnapshot();
        // A pure Bedrock attachable has no Java baked model: only bail when neither the vanilla
        // item model nor an attachable snapshot exists.
        if (item.isEmpty() && (snapshot == null || snapshot.isEmpty())) {
            return;
        }

        PlayerModel model = this.getParentModel();
        final AttachableQueryContext.LogicalHand logicalHand = hand == InteractionHand.MAIN_HAND
                ? AttachableQueryContext.LogicalHand.MAIN_HAND
                : AttachableQueryContext.LogicalHand.OFF_HAND;
        final AttachableRenderResult attachableResult =
                ViaBedrockUtility.getInstance().getAttachableRuntimeManager().renderThirdPerson(
                        state, snapshot, logicalHand, arm, model, poseStack, bufferSource, packedLight);
        if (attachableResult != AttachableRenderResult.NOT_APPLICABLE) {
            return;
        }

        // heldOnHead keeps its original reachability for non-attachable vanilla item models.
        if (!item.isEmpty() && state.isUsingItem && state.useItemHand == hand
                && state.attackTime < 1.0E-5F && !state.heldOnHead.isEmpty()) {
            renderItemHeldToEye(state, arm, poseStack, bufferSource, packedLight);
            return;
        }

        final BedrockPlayerModelMetadata metadata = BedrockPlayerModelMetadata.get(model);
        if (metadata == null) {
            warnMissingMetadata();
            super.renderArmWithItem(state, item, arm, poseStack, bufferSource, packedLight);
            return;
        }

        AttachPoint attach = resolveAttach(metadata, arm);
        if (attach == null) {
            warnMissingAttach();
            super.renderArmWithItem(state, item, arm, poseStack, bufferSource, packedLight);
            return;
        }
        logAttachPoint(state, arm, attach);

        poseStack.pushPose();
        translateToBedrockAwareJavaHand(model, metadata, attach, arm, poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        applyItemOffset(poseStack, arm, JAVA_HAND_X_OFFSET, JAVA_HAND_Y_OFFSET, JAVA_HAND_Z_OFFSET);
        item.render(poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private static void translateToBedrockAwareJavaHand(PlayerModel model, BedrockPlayerModelMetadata metadata,
                                                        AttachPoint attach, HumanoidArm arm, PoseStack poseStack) {
        model.root().translateAndRotate(poseStack);

        ModelPart armPart = arm == HumanoidArm.LEFT ? model.leftArm : model.rightArm;
        Vector3f pivot = attach.armBone().pivot();
        float originalX = armPart.x;
        float originalY = armPart.y;
        float originalZ = armPart.z;
        IModelPart armMixin = (IModelPart) (Object) armPart;
        try {
            armPart.x = pivot.x;
            armPart.y = pivot.y;
            armPart.z = pivot.z;
            if (metadata.slim()) {
                armPart.x += 0.5F * (arm == HumanoidArm.RIGHT ? 1.0F : -1.0F);
            }
            // The visual player arm uses VBU's X/Z cancellation; item mounting needs Java's hand basis.
            armMixin.viaBedrockUtility$setNeededOffset(false);
            armPart.translateAndRotate(poseStack);
        } finally {
            armPart.x = originalX;
            armPart.y = originalY;
            armPart.z = originalZ;
            armMixin.viaBedrockUtility$setNeededOffset(true);
        }
    }

    private static void applyItemOffset(PoseStack poseStack, HumanoidArm arm, float x, float y, float z) {
        boolean left = arm == HumanoidArm.LEFT;
        poseStack.translate(left ? -x : x, y, z);
    }

    private void renderItemHeldToEye(PlayerRenderState state, HumanoidArm arm, PoseStack poseStack,
                                     MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        PlayerModel model = this.getParentModel();
        model.root().translateAndRotate(poseStack);
        float originalXRot = model.getHead().xRot;
        model.getHead().xRot = Mth.clamp(model.getHead().xRot, -0.5235988F, 1.5707964F);
        model.getHead().translateAndRotate(poseStack);
        model.getHead().xRot = originalXRot;
        CustomHeadLayer.translateToHead(poseStack, CustomHeadLayer.Transforms.DEFAULT);
        boolean left = arm == HumanoidArm.LEFT;
        poseStack.translate((left ? -2.5F : 2.5F) / 16.0F, -0.0625F, 0.0F);
        state.heldOnHead.render(poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private static AttachPoint resolveAttach(BedrockPlayerModelMetadata metadata, HumanoidArm arm) {
        boolean left = arm == HumanoidArm.LEFT;
        BedrockPlayerModelMetadata.Bone itemBone = metadata.firstBone(left ? "leftitem" : "rightitem");
        if (itemBone != null) {
            BedrockPlayerModelMetadata.Bone parentBone = metadata.bone(itemBone.parentKey());
            if (parentBone != null) {
                return new AttachPoint("item_bone_java_hand", itemBone, parentBone, itemBone.pivot());
            }
        }

        String armName = left ? "left_arm" : "right_arm";
        String compactArmName = left ? "leftarm" : "rightarm";
        BedrockPlayerModelMetadata.Bone armBone = metadata.firstBone(armName, compactArmName);
        BedrockPlayerModelMetadata.LocatorMatch locator = metadata.findLocatorForArm(
                left ? "left_hand" : "right_hand",
                armBone,
                itemBone
        );
        if (locator != null) {
            BedrockPlayerModelMetadata.Bone armTarget = armBone == null ? locator.bone() : armBone;
            return new AttachPoint("locator:" + locator.locator().originalName(), locator.bone(), armTarget, locator.locator().point());
        }

        if (armBone != null) {
            BedrockPlayerModelMetadata.Bounds bounds = armBone.bounds();
            if (bounds == null) {
                bounds = metadata.directChildBounds(armBone);
            }
            if (bounds != null) {
                // VBU compatibility fallback, not an official Bedrock attachable semantic.
                return new AttachPoint("arm_bounds_fallback", armBone, armBone, bounds.handFallbackPoint());
            }
        }

        return null;
    }

    private static void warnMissingMetadata() {
        if (!warnedMissingMetadata) {
            warnedMissingMetadata = true;
            ViaBedrockUtilityNeoForge.LOGGER.warn("[VBU] Custom player model has no Bedrock attach metadata; using vanilla hand item transform");
        }
    }

    private static void warnMissingAttach() {
        if (!warnedMissingAttach) {
            warnedMissingAttach = true;
            ViaBedrockUtilityNeoForge.LOGGER.warn("[VBU] No Bedrock hand attach point found; using vanilla hand item transform");
        }
    }

    private static void logAttachPoint(PlayerRenderState state, HumanoidArm arm, AttachPoint attach) {
        String playerName = state.name == null ? "unknown" : state.name;
        String key = state.id + ":" + playerName + ":" + arm + ":" + attach.kind() + ":" + attach.bone().originalName();
        if (loggedAttachPoints.add(key)) {
            Vector3f point = attach.point();
            ViaBedrockUtilityNeoForge.LOGGER.debug(
                    "[VBU] Bedrock hand attach player='{}' id={} arm={} kind={} bone='{}' armBone='{}' point=({}, {}, {})",
                    playerName,
                    state.id,
                    arm,
                    attach.kind(),
                    attach.bone().originalName(),
                    attach.armBone().originalName(),
                    point.x,
                    point.y,
                    point.z
            );
        }
    }

    private record AttachPoint(String kind, BedrockPlayerModelMetadata.Bone bone,
                               BedrockPlayerModelMetadata.Bone armBone, Vector3f point) {
    }
}
