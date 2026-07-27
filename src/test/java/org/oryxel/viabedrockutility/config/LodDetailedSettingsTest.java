package org.oryxel.viabedrockutility.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LodDetailedSettingsTest {
    @Test
    void normalizesUnsafeCustomValues() {
        LodDetailedSettings value = new LodDetailedSettings(
                -1.0, 0,
                40.0, 4,
                900.0, 100,
                -10.0,
                900.0,
                -1,
                900,
                0,
                true,
                2.0,
                20.0,
                1,
                true,
                80,
                20
        ).normalized();

        assertEquals(0.0, value.tier1Distance());
        assertEquals(1, value.tier1FrameInterval());
        assertEquals(512.0, value.tier3Distance());
        assertEquals(60, value.tier3FrameInterval());
        assertEquals(12.0, value.frozenMeshEnterDistance());
        assertEquals(11.0, value.frozenMeshExitDistance());
        assertEquals(16 * LodDetailedSettings.MIB, value.frozenMeshMaxGpuBytes());
        assertEquals(80, value.particleTickLodNearDistance());
        assertEquals(80, value.particleTickLodFarDistance());
    }
}
