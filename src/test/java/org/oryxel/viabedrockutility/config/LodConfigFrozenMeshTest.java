package org.oryxel.viabedrockutility.config;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LodConfigFrozenMeshTest {
    @Test
    void oldPerformanceConfigMigratesToEnabled() {
        LodConfig config = new LodConfig();
        config.setPreset(LodConfig.Preset.PERFORMANCE);
        JsonObject old = new JsonObject();
        old.addProperty("preset", "PERFORMANCE");

        assertTrue(config.migrate(old));
        assertTrue(config.isFrozenMeshEnabled());
        assertEquals(18.0, config.getFrozenMeshEnterDistance());
        assertEquals(15.0, config.getFrozenMeshExitDistance());
        assertEquals(134_217_728L, config.getFrozenMeshMaxGpuBytes());
    }

    @Test
    void presetsKeepAVisibleAnimationRadius() {
        LodConfig config = new LodConfig();
        config.applyPreset(LodConfig.Preset.HIGH_QUALITY);
        assertTrue(config.isFrozenMeshEnabled());
        assertEquals(32.0, config.getFrozenMeshEnterDistance());
        assertEquals(28.0, config.getFrozenMeshExitDistance());
        config.applyPreset(LodConfig.Preset.BALANCED);
        assertTrue(config.isFrozenMeshEnabled());
        assertEquals(24.0, config.getFrozenMeshEnterDistance());
        assertEquals(20.0, config.getFrozenMeshExitDistance());
        config.applyPreset(LodConfig.Preset.PERFORMANCE);
        assertTrue(config.isFrozenMeshEnabled());
        assertEquals(18.0, config.getFrozenMeshEnterDistance());
        assertEquals(15.0, config.getFrozenMeshExitDistance());
        config.applyPreset(LodConfig.Preset.EXTREME);
        assertTrue(config.isFrozenMeshEnabled());
        assertEquals(12.0, config.getFrozenMeshEnterDistance());
        assertEquals(9.0, config.getFrozenMeshExitDistance());
        assertEquals(128 * LodDetailedSettings.MIB, config.getFrozenMeshMaxGpuBytes());
        config.applyPreset(LodConfig.Preset.PERFORMANCE);
        assertEquals(128 * LodDetailedSettings.MIB, config.getFrozenMeshMaxGpuBytes());
        config.setFrozenMeshEnabled(false);
        config.applyPreset(LodConfig.Preset.CUSTOM);
        assertFalse(config.isFrozenMeshEnabled());
    }

    @Test
    void customMigrationPreservesAnExplicitFrozenMeshValue() {
        LodConfig config = new LodConfig();
        config.setPreset(LodConfig.Preset.CUSTOM);
        config.setFrozenMeshEnabled(true);
        JsonObject source = new JsonObject();
        source.addProperty("frozenMeshEnabled", true);
        source.addProperty("frozenMeshEnterDistance", 18.0);
        source.addProperty("frozenMeshExitDistance", 15.0);
        source.addProperty("frozenMeshMaxGpuBytes", 134_217_728L);

        config.migrate(source);

        assertTrue(config.isFrozenMeshEnabled());
    }

    @Test
    void legacyCustomConfigMigratesToManualMode() {
        LodConfig config = new LodConfig();
        config.setPreset(LodConfig.Preset.CUSTOM);
        JsonObject source = new JsonObject();
        source.addProperty("preset", "CUSTOM");

        config.migrate(source);

        assertEquals(LodConfig.OptimizationMode.MANUAL, config.getOptimizationMode());
        assertEquals(LodConfig.Preset.CUSTOM, config.getManualPreset());
    }
}
