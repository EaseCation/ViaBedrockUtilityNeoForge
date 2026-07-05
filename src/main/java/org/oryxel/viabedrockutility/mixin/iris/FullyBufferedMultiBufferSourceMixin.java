package org.oryxel.viabedrockutility.mixin.iris;

import net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource;
import net.irisshaders.batchedentityrendering.impl.ordering.GraphTranslucencyRenderOrderManager;
import org.oryxel.viabedrockutility.renderer.iris.VbuShaderAwareRenderOrderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Replaces the {@link GraphTranslucencyRenderOrderManager} that {@link FullyBufferedMultiBufferSource}'s
 * constructor hard-codes with our {@link VbuShaderAwareRenderOrderManager}. That wrapper behaves identically
 * to the original while a shaderpack is active, but downgrades to Iris' cheap insertion-order manager while
 * shaders are OFF — eliminating the per-{@code endBatch} feedback-arc-set DFS that dominates the render
 * thread in hologram-heavy lobbies.
 *
 * <p>This mixin lives in {@code viabedrockutility.iris.mixins.json} ({@code "required": false}), so if Iris
 * is absent the whole config — and therefore this class and {@link VbuShaderAwareRenderOrderManager} — is
 * never loaded. {@code require = 1} makes the redirect fail loudly if a future Iris version moves the target
 * (so the optimization can never silently no-op).
 */
@Mixin(FullyBufferedMultiBufferSource.class)
public abstract class FullyBufferedMultiBufferSourceMixin {
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "NEW",
                    target = "net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager"
            ),
            require = 1
    )
    private GraphTranslucencyRenderOrderManager vbu$shaderAwareOrderManager() {
        return new VbuShaderAwareRenderOrderManager();
    }
}
