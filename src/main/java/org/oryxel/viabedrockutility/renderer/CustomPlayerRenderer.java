package org.oryxel.viabedrockutility.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import net.easecation.bedrockmotion.pack.definitions.AnimationDefinitions;
import net.easecation.bedrockmotion.pack.PackManager;
import org.oryxel.viabedrockutility.animation.PlayerAnimationRuntime;
import org.oryxel.viabedrockutility.animation.PlayerAnimationState;
import org.oryxel.viabedrockutility.mixin.interfaces.ICustomPlayerRendererHolder;
import org.oryxel.viabedrockutility.attachable.AttachableItemSnapshot;
import org.oryxel.viabedrockutility.attachable.AttachableOwnerSnapshot;
import org.oryxel.viabedrockutility.ViaBedrockUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.Map;

public class CustomPlayerRenderer extends PlayerRenderer {
    private final ResourceLocation texture;
    private final List<AnimatedSkinOverlay> overlays = new ArrayList<>();
    private volatile BedrockPlayerWorldPose thirdPersonPose;
    private PlayerAnimationRuntime playerAnimationRuntime;

    public CustomPlayerRenderer(final EntityRendererProvider.Context ctx, final PlayerModel model, final boolean slim, ResourceLocation texture) {
        super(ctx, slim);

        if (model != null) {
            this.model = model;
        }

        this.texture = texture;
        this.layers.removeIf(layer -> layer instanceof PlayerItemInHandLayer<?, ?>);
        this.addLayer(new BedrockPlayerPoseCaptureLayer(this));
        this.addLayer(new BedrockPlayerItemInHandLayer(this));
        this.addLayer(new AnimatedOverlayFeatureRenderer(this));
    }

    @Override
    public PlayerRenderState createRenderState() {
        PlayerRenderState state = super.createRenderState();
        ((ICustomPlayerRendererHolder) state).viaBedrockUtility$setCustomPlayerRenderer(this);
        return state;
    }

    @Override
    public void extractRenderState(AbstractClientPlayer player, PlayerRenderState state, float partialTick) {
        super.extractRenderState(player, state, partialTick);
        final var attachables = ViaBedrockUtility.getInstance().getAttachableRuntimeManager();
        ((ICustomPlayerRendererHolder) state).viaBedrockUtility$setHandSnapshots(
                attachables.snapshotIfCandidate(player.getMainHandItem()),
                attachables.snapshotIfCandidate(player.getOffhandItem()));
        ((ICustomPlayerRendererHolder) state).viaBedrockUtility$setHandItemIdentifiers(
                itemIdentifier(player.getMainHandItem()), itemIdentifier(player.getOffhandItem()));
        ((ICustomPlayerRendererHolder) state).viaBedrockUtility$setOwnerSnapshot(
                new AttachableOwnerSnapshot(player.getUUID(), "minecraft:player",
                        player.getAttackAnim(partialTick), state.xRot,
                        state.yRot));
        ((ICustomPlayerRendererHolder) state).viaBedrockUtility$setPlayerAnimationState(
                PlayerAnimationState.thirdPerson(player, state, partialTick));
    }

    private static ResourceLocation itemIdentifier(net.minecraft.world.item.ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerRenderState PlayerRenderState) {
        return this.texture;
    }

    public ResourceLocation getPlayerTexture() {
        return this.texture;
    }

    public PlayerModel getPlayerModel() {
        return this.model;
    }

    void captureThirdPersonPose(PlayerRenderState state, PoseStack poseStack) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        final AttachableOwnerSnapshot owner = ((ICustomPlayerRendererHolder) state)
                .viaBedrockUtility$getOwnerSnapshot();
        final long tick = minecraft.level.getGameTime();
        if (owner.uuid() == null || !ViaBedrockUtility.getInstance().getPlayerPoseDemand()
                .isRequested(owner.uuid(), tick)) {
            this.thirdPersonPose = null;
            return;
        }
        final BedrockPlayerModelMetadata metadata = BedrockPlayerModelMetadata.get(this.model);
        if (metadata == null) {
            this.thirdPersonPose = null;
            return;
        }

        // Entity rendering is camera-relative. Restoring the camera translation turns the exact
        // renderer-entry PoseStack (including scale, swimming/sleeping and mod transforms) into world space.
        final var cameraPosition = minecraft.gameRenderer.getMainCamera().getPosition();
        final Matrix4f worldPresentation = new Matrix4f()
                .translation((float) cameraPosition.x, (float) cameraPosition.y, (float) cameraPosition.z)
                .mul(poseStack.last().pose());
        this.thirdPersonPose = BedrockPlayerWorldPose.capture(
                owner.uuid(), tick, worldPresentation, metadata);
    }

    public BedrockPlayerWorldPose thirdPersonPose(UUID ownerUuid, long currentTick) {
        final BedrockPlayerWorldPose pose = this.thirdPersonPose;
        return pose != null && pose.isFresh(ownerUuid, currentTick) ? pose : null;
    }

    public List<AnimatedSkinOverlay> getOverlays() {
        return Collections.unmodifiableList(overlays);
    }

    public void addOverlay(AnimatedSkinOverlay overlay) {
        this.overlays.add(overlay);
    }

    public void tickOverlays() {
        for (AnimatedSkinOverlay overlay : overlays) {
            overlay.tick();
        }
    }

    public void playAnimationOnce(final String name, final AnimationDefinitions.AnimationData data) {
        if (this.playerAnimationRuntime != null) {
            this.playerAnimationRuntime.playOnce(name, data);
        }
    }

    public void setPlayerAnimationRuntime(PackManager packs, Map<String, String> animationOverrides) {
        this.playerAnimationRuntime = new PlayerAnimationRuntime(packs, animationOverrides);
    }

    public PlayerAnimationRuntime playerAnimationRuntime() {
        return playerAnimationRuntime;
    }

    public boolean sampleFirstPerson(PlayerAnimationState state) {
        return playerAnimationRuntime != null
                && playerAnimationRuntime.sampleFirstPerson(this.model, state);
    }
}
