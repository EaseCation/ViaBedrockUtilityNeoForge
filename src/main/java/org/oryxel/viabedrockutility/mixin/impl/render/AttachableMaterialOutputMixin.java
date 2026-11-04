package org.oryxel.viabedrockutility.mixin.impl.render;

import com.mojang.blaze3d.opengl.GlCommandEncoder;
import com.mojang.blaze3d.opengl.GlRenderPass;
import net.neoforged.fml.ModList;
import org.oryxel.viabedrockutility.attachable.AttachableMaterials;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/** Run after native/Iris setup, immediately before the draw, including deferred buffer flushes. */
@Mixin(GlCommandEncoder.class)
public abstract class AttachableMaterialOutputMixin {
    @Inject(method = "trySetup", at = @At("RETURN"))
    private void vbu$ownedMaterialOutput(GlRenderPass pass, Collection<String> uniforms,
                                         CallbackInfoReturnable<Boolean> result) {
        var pipeline = ((org.oryxel.viabedrockutility.mixin.impl.accessor.GlRenderPassAccessor) pass).vbu$getPipeline();
        if (result.getReturnValueZ() && pipeline != null
                && AttachableMaterials.owns(pipeline.info()) && ModList.get().isLoaded("iris")) {
            org.oryxel.viabedrockutility.renderer.iris.AttachableMaterialOutput.bind();
        }
    }
}
