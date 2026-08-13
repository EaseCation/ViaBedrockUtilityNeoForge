package org.oryxel.viabedrockutility.renderer;

import net.minecraft.client.renderer.entity.EntityRenderer;
import org.oryxel.viabedrockutility.entity.CustomEntityTicker;
import org.oryxel.viabedrockutility.payload.PayloadHandler;

import java.util.UUID;

public final class EntityRendererLookup {
    private EntityRendererLookup() {
    }

    public static EntityRenderer<?, ?> find(final UUID uuid, final PayloadHandler payloadHandler) {
        final EntityRenderer<?, ?> playerRenderer = payloadHandler.getCachedPlayerRenderers().get(uuid);
        if (playerRenderer != null) {
            return playerRenderer;
        }

        final CustomEntityTicker customEntity = payloadHandler.getCachedCustomEntities().get(uuid);
        return customEntity == null ? null : customEntity.getRenderer();
    }
}
