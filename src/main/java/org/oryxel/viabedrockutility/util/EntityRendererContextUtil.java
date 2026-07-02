package org.oryxel.viabedrockutility.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import org.oryxel.viabedrockutility.mixin.impl.accessor.EntityRenderDispatcherAccessor;

public final class EntityRendererContextUtil {
    private EntityRendererContextUtil() {}

    /**
     * Builds an {@link EntityRendererProvider.Context} using the client's real, resource-reload-backed
     * {@link EquipmentAssetManager} instead of a throwaway {@code new EquipmentAssetManager()}: a freshly
     * constructed one is never registered as a reload listener, so its equipment map stays empty forever
     * and HumanoidArmorLayer/WingsLayer/CapeLayer silently render nothing for any renderer built from it.
     */
    public static EntityRendererProvider.Context build(final Minecraft client) {
        final EntityRenderDispatcher entityRenderDispatcher = client.getEntityRenderDispatcher();
        final EquipmentAssetManager equipmentAssets = ((EntityRenderDispatcherAccessor) entityRenderDispatcher)
                .viaBedrockUtility$getEquipmentAssets();
        return new EntityRendererProvider.Context(entityRenderDispatcher,
                client.getItemModelResolver(), client.getMapRenderer(), client.getBlockRenderer(),
                client.getResourceManager(), client.getEntityModels(), equipmentAssets, client.font);
    }
}
