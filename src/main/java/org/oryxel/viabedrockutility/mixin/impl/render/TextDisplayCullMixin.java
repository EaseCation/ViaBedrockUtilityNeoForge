package org.oryxel.viabedrockutility.mixin.impl.render;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import org.oryxel.viabedrockutility.config.LodConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Distance-culls {@code text_display} (hologram) entities. Each visible text display triggers a full Iris
 * {@code endBatch()} flush via ImmediatelyFast's per-display background draw, which — in a hologram-heavy
 * lobby — is the single biggest render-thread cost (~12% in JFR). Skipping far holograms at
 * {@link EntityRenderer#shouldRender} removes that cost at the source: the entity is never dispatched, so
 * {@code renderInner} and its {@code endBatch} never run.
 *
 * <p>Targets the base {@link EntityRenderer} because {@code DisplayRenderer} does not override
 * {@code shouldRender} (it inherits this method), and guards on {@code instanceof Display.TextDisplay} so it
 * only ever affects text displays — every other entity, including VBU's own {@code CustomEntityRenderer}
 * (which overrides {@code shouldRender} and therefore never reaches this injection), is untouched.</p>
 *
 * <p>Controlled by {@link LodConfig#getTextDisplayCullDistance()} (0 = never cull; generous by default since
 * holograms are often read from a distance).</p>
 */
@Mixin(EntityRenderer.class)
public abstract class TextDisplayCullMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void viaBedrockUtility$cullDistantTextDisplay(final Entity entity, final Frustum frustum,
                                                          final double camX, final double camY, final double camZ,
                                                          final CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Display.TextDisplay)) {
            return;
        }
        final double cull = LodConfig.getInstance().getTextDisplayCullDistance();
        if (cull <= 0.0) {
            return;
        }
        if (entity.distanceToSqr(camX, camY, camZ) > cull * cull) {
            cir.setReturnValue(false);
        }
    }
}
