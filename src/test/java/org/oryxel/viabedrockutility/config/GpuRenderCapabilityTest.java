package org.oryxel.viabedrockutility.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GpuRenderCapabilityTest {
    private static final long GIB = 1L << 30;

    @Test
    void genericIntegratedRadeonUsesEightChunks() {
        assertEquals(8, GpuRenderCapability.recommendedInitialRenderDistance(
                "AMD Radeon(TM) Graphics", 512L << 20));
    }

    @Test
    void olderIntelIntegratedGraphicsUsesEightChunks() {
        assertEquals(8, GpuRenderCapability.recommendedInitialRenderDistance(
                "Intel(R) UHD Graphics 620", 1 * GIB));
    }

    @Test
    void modernIntegratedGraphicsKeepsTwelveChunks() {
        assertEquals(12, GpuRenderCapability.recommendedInitialRenderDistance(
                "AMD Radeon 780M Graphics", 512L << 20));
        assertEquals(12, GpuRenderCapability.recommendedInitialRenderDistance(
                "Intel(R) Iris(R) Xe Graphics", 1 * GIB));
    }

    @Test
    void modernDiscreteGraphicsKeepsTwelveChunks() {
        assertEquals(12, GpuRenderCapability.recommendedInitialRenderDistance(
                "NVIDIA GeForce RTX 4060", 8 * GIB));
    }

    @Test
    void unknownGpuDoesNotOverrideVanillaDefault() {
        assertEquals(12, GpuRenderCapability.recommendedInitialRenderDistance("Unknown GPU", 0));
    }
}
