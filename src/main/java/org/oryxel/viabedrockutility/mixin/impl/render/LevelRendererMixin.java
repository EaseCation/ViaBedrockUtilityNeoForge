package org.oryxel.viabedrockutility.mixin.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.oryxel.viabedrockutility.renderer.VbuRenderMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @WrapMethod(method = "renderEntity")
    private void vbu$worldAnchorSpace(Entity entity, double cameraX, double cameraY, double cameraZ,
                                     float partialTick, PoseStack poses, MultiBufferSource buffers, Operation<Void> original) {
        try (var world = org.oryxel.viabedrockutility.attachable.AttachableWorldPoses.world(entity, cameraX, cameraY, cameraZ)) {
            original.call(entity, cameraX, cameraY, cameraZ, partialTick, poses, buffers);
        }
    }
    @Inject(method = "renderEntities", at = @At("HEAD"))
    private void vbu$beginEntityFrame(PoseStack poses,
                                      MultiBufferSource.BufferSource buffers,
                                      Camera camera,
                                      DeltaTracker deltaTracker,
                                      List<Entity> entities,
                                      CallbackInfo ci) {
        VbuRenderMetrics.beginFrame();
    }

}
