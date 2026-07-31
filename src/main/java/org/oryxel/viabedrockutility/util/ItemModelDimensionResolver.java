package org.oryxel.viabedrockutility.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ItemModelDimensionResolver {

    private static final ResourceLocation VIABEDROCK_OVERWORLD =
            ResourceLocation.fromNamespaceAndPath("viabedrock", "overworld");

    public static ResourceKey<Level> normalize(ResourceKey<Level> dimension) {
        if (dimension != null && dimension.location().equals(VIABEDROCK_OVERWORLD)) {
            return Level.OVERWORLD;
        }
        return dimension;
    }

    private ItemModelDimensionResolver() {
    }
}
