package org.oryxel.viabedrockutility.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HardwareProfileTest {
    private static final long GHZ = 1_000_000_000L;
    private static final long GIB = 1L << 30;

    @Test
    void knownCpuGenerationIgnoresMisreportedBaseClock() {
        HardwareProfile profile = HardwareProfile.assess(
                "AMD Ryzen 5 5500U with Radeon Graphics",
                6,
                12,
                (long) (2.1 * GHZ),
                "AMD Radeon(TM) Graphics",
                512L << 20
        );

        assertEquals(2, profile.performanceScore());
        assertEquals(LodConfig.Preset.PERFORMANCE, profile.recommendedPreset());
    }

    @Test
    void zeroScoreUsesExtremePreset() {
        HardwareProfile profile = new HardwareProfile("CPU", 4, 8, 0, "GPU", 0, 0);

        assertEquals(LodConfig.Preset.EXTREME, profile.recommendedPreset());
    }

    @Test
    void reportedBoostClockDoesNotChangeKnownCpuTier() {
        HardwareProfile profile = HardwareProfile.assess(
                "AMD Ryzen 5 5500U with Radeon Graphics",
                6,
                12,
                (long) (4.05 * GHZ),
                "AMD Radeon(TM) Graphics",
                512L << 20
        );

        assertEquals(2, profile.performanceScore());
        assertEquals(LodConfig.Preset.PERFORMANCE, profile.recommendedPreset());
    }

    @Test
    void olderMainstreamDesktopUsesPerformancePreset() {
        HardwareProfile profile = HardwareProfile.assess(
                "Intel Core i5-10400F",
                6,
                12,
                (long) (4.3 * GHZ),
                "NVIDIA GeForce GTX 1650",
                4 * GIB
        );

        assertEquals(2, profile.performanceScore());
        assertEquals(LodConfig.Preset.PERFORMANCE, profile.recommendedPreset());
    }

    @Test
    void modernMainstreamDesktopUsesBalancedPreset() {
        HardwareProfile profile = HardwareProfile.assess(
                "Intel Core i5-12400F",
                6,
                12,
                (long) (2.5 * GHZ),
                "Intel UHD Graphics",
                512L << 20
        );

        assertEquals(5, profile.performanceScore());
        assertEquals(LodConfig.Preset.BALANCED, profile.recommendedPreset());
    }

    @Test
    void modernDesktopUsesHighQualityPreset() {
        HardwareProfile profile = HardwareProfile.assess(
                "AMD Ryzen 7 7800X3D",
                8,
                16,
                (long) (5.0 * GHZ),
                "NVIDIA GeForce RTX 4070",
                12 * GIB
        );

        assertEquals(LodConfig.Preset.HIGH_QUALITY, profile.recommendedPreset());
    }

    @Test
    void gpuClassDoesNotChangeCpuBoundPreset() {
        HardwareProfile integrated = HardwareProfile.assess(
                "Intel Core i5-12400F", 6, 12, (long) (2.5 * GHZ),
                "Intel UHD Graphics", 512L << 20);
        HardwareProfile discrete = HardwareProfile.assess(
                "Intel Core i5-12400F", 6, 12, (long) (2.5 * GHZ),
                "NVIDIA GeForce RTX 4090", 24 * GIB);

        assertEquals(integrated.performanceScore(), discrete.performanceScore());
        assertEquals(integrated.recommendedPreset(), discrete.recommendedPreset());
    }

    @Test
    void entryLevelCpuUsesExtremePreset() {
        HardwareProfile profile = HardwareProfile.assess(
                "Intel N100", 4, 4, (long) (3.4 * GHZ),
                "Intel UHD Graphics", 512L << 20);

        assertEquals(0, profile.performanceScore());
        assertEquals(LodConfig.Preset.EXTREME, profile.recommendedPreset());
    }
}
