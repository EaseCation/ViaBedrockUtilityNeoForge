package org.oryxel.viabedrockutility.mixin.impl.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.renderer.DeferredNameTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Defers every entity name tag (while a Bedrock session is active) so they render after all entity
 * geometry instead of interleaved with it. See {@link DeferredNameTag} for the full rationale.
 *
 * <p>Both {@code drawInBatch} calls inside {@code renderNameTag} (the faint SEE_THROUGH pass and the opaque
 * NORMAL pass) share this one signature, so a single redirect covers both.
 *
 * <p>Deferring <b>all</b> name tags - not just VBU's custom entity/player renderers - is deliberate:
 * "floating text" on servers is implemented via armor-stand name tags (which also flow through this very
 * method) and TextDisplay panels. Deferring only VBU renderers would split name tags into two layers
 * (deferred VBU names drawn on top of inline armor-stand floating text), breaking their relative ordering.
 * With every name tag in the same deferred pass they all depth-sort consistently via the NORMAL pass's
 * depth write - nearer covers farther - exactly as vanilla name tags order among themselves. Gated by
 * {@code isViaBedrockPresent()} so a non-Bedrock session keeps the exact vanilla inline behavior.
 */
@Mixin(EntityRenderer.class)
public class EntityRendererNameTagMixin {

    @Redirect(
            method = "renderNameTag",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"),
            require = 0
    )
    private void viaBedrockUtility$deferNameTag(final Font font, final Component text, final float x, final float y,
                                                final int color, final boolean dropShadow, final Matrix4f pose,
                                                final MultiBufferSource bufferSource, final Font.DisplayMode mode,
                                                final int backgroundColor, final int packedLight) {
        if (ViaBedrockUtility.getInstance().isViaBedrockPresent()) {
            DeferredNameTag.enqueue(font, text, x, y, color, pose, mode, backgroundColor, packedLight);
        } else {
            font.drawInBatch(text, x, y, color, dropShadow, pose, bufferSource, mode, backgroundColor, packedLight);
        }
    }
}
