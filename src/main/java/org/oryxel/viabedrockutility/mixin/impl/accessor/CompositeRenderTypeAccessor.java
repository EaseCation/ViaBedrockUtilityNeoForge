package org.oryxel.viabedrockutility.mixin.impl.accessor;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.RenderType$CompositeRenderType")
public interface CompositeRenderTypeAccessor {
    @Accessor("renderPipeline")
    RenderPipeline viaBedrockUtility$getRenderPipeline();

    @Accessor("state")
    RenderType.CompositeState viaBedrockUtility$getCompositeState();
}
