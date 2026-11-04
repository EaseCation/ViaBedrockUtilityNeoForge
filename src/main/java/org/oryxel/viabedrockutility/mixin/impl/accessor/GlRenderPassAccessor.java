package org.oryxel.viabedrockutility.mixin.impl.accessor;

import com.mojang.blaze3d.opengl.GlRenderPass;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GlRenderPass.class)
public interface GlRenderPassAccessor {
    @Accessor("pipeline") GlRenderPipeline vbu$getPipeline();
}
