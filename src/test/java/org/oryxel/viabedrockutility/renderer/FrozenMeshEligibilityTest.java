package org.oryxel.viabedrockutility.renderer;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrozenMeshEligibilityTest {
    @Test
    void acceptsOnlyTheThreeOpaqueEntityRenderTypes() {
        ResourceLocation texture = ResourceLocation.withDefaultNamespace("stone");

        assertTrue(FrozenMeshEligibility.isStandardRenderType(RenderType.entitySolid(texture), texture));
        assertTrue(FrozenMeshEligibility.isStandardRenderType(RenderType.entityCutout(texture), texture));
        assertTrue(FrozenMeshEligibility.isStandardRenderType(RenderType.entityCutoutNoCull(texture), texture));
        assertFalse(FrozenMeshEligibility.isStandardRenderType(RenderType.entityTranslucent(texture), texture));
        assertFalse(FrozenMeshEligibility.isStandardRenderType(
                RenderType.entitySolid(ResourceLocation.withDefaultNamespace("dirt")), texture));
    }
}
