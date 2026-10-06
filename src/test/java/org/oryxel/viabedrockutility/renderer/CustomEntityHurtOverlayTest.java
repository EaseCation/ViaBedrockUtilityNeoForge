package org.oryxel.viabedrockutility.renderer;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;
import org.oryxel.viabedrockutility.entity.CustomEntityHurtTracker;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomEntityHurtOverlayTest {
    @Test
    void damageUsesRedOverlayThenRestoresCachedPoseSubmission() {
        final CustomEntityHurtTracker tracker = new CustomEntityHurtTracker();
        final CustomEntityRenderer.CustomEntityRenderState state = new CustomEntityRenderer.CustomEntityRenderState();
        final UUID uuid = UUID.randomUUID();
        assertEquals(OverlayTexture.NO_OVERLAY, state.packedOverlay());
        assertTrue(state.canUsePoseMesh(true));
        tracker.markHurt(uuid, 100L);
        state.setHasRedOverlay(tracker.isHurt(uuid, 100L));
        assertEquals(OverlayTexture.pack(OverlayTexture.NO_WHITE_U, OverlayTexture.RED_OVERLAY_V), state.packedOverlay());
        assertFalse(state.canUsePoseMesh(true));
        assertFalse(state.canUsePoseMesh(false));
        state.setHasRedOverlay(tracker.isHurt(uuid, 110L));
        assertEquals(OverlayTexture.NO_OVERLAY, state.packedOverlay());
        assertTrue(state.canUsePoseMesh(true));
        assertFalse(state.canUsePoseMesh(false));
    }

    @Test
    void hurtSubmissionReturnsBeforeReadingOrRebuildingAnyCachedMesh() {
        // 不初始化缓存及 GPU 后端；只要受击分支访问它们，测试就会失败。
        final CustomEntityRenderer<Entity> renderer = new ObjenesisStd().newInstance(CustomEntityRenderer.class);
        final CustomEntityRenderer.CustomEntityRenderState state = new CustomEntityRenderer.CustomEntityRenderState();
        state.setHasRedOverlay(true);
        assertFalse(renderer.tryQueuePoseMesh(state, null, null, null, 0, false, null));
    }
}
