package org.oryxel.viabedrockutility.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ItemModelDimensionResolverTest {

    @Test
    void mapsViaBedrockOverworldToVanillaOverworld() {
        ResourceKey<Level> alternateOverworld = dimension("viabedrock", "overworld");

        assertSame(Level.OVERWORLD, ItemModelDimensionResolver.normalize(alternateOverworld));
    }

    @Test
    void preservesVanillaAndUnrelatedDimensions() {
        ResourceKey<Level> alternateNether = dimension("viabedrock", "the_nether");

        assertSame(Level.OVERWORLD, ItemModelDimensionResolver.normalize(Level.OVERWORLD));
        assertSame(Level.NETHER, ItemModelDimensionResolver.normalize(Level.NETHER));
        assertSame(alternateNether, ItemModelDimensionResolver.normalize(alternateNether));
        assertNull(ItemModelDimensionResolver.normalize(null));
    }

    private static ResourceKey<Level> dimension(String namespace, String path) {
        return ResourceKey.create(
                Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(namespace, path)
        );
    }
}
