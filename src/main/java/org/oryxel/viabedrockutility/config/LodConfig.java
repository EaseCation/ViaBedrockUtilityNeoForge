package org.oryxel.viabedrockutility.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import org.oryxel.viabedrockutility.renderer.FrozenEntityMeshCache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LodConfig {
    static final int CURRENT_CONFIG_VERSION = 3;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static LodConfig INSTANCE;

    private int configVersion = CURRENT_CONFIG_VERSION;
    private OptimizationMode optimizationMode = OptimizationMode.AUTO;
    private Preset manualPreset = Preset.BALANCED;
    private LodDetailedSettings customSettings = LodDetailedSettings.balancedDefaults();
    private transient HardwareProfile hardwareProfile;

    public enum Preset {
        HIGH_QUALITY,
        BALANCED,
        PERFORMANCE,
        EXTREME,
        CUSTOM
    }

    public enum OptimizationMode {
        AUTO,
        MANUAL
    }

    public static class LodTier {
        public double distance;
        public int frameInterval;

        public LodTier() {}

        public LodTier(double distance, int frameInterval) {
            this.distance = distance;
            this.frameInterval = frameInterval;
        }
    }

    private Preset preset = Preset.BALANCED;
    private LodTier tier1 = new LodTier(18.0, 3);
    private LodTier tier2 = new LodTier(36.0, 5);
    private LodTier tier3 = new LodTier(56.0, 8);
    private double renderCullDistance = 40.0;
    private boolean frozenMeshEnabled = false;
    private double frozenMeshEnterDistance = 18.0;
    private double frozenMeshExitDistance = 15.0;
    private long frozenMeshMaxGpuBytes = 134_217_728L;

    // Distance beyond which text_display (hologram) entities are skipped entirely (EntityRenderer.shouldRender
    // returns false — see mixin.impl.render.TextDisplayCullMixin). Each visible text_display triggers a full
    // Iris endBatch flush via ImmediatelyFast, so culling far holograms cuts that cost at the source. Kept
    // generous by default (holograms are often read from a distance); 0 = never cull. NOTE: a newly-added
    // field absent from an existing config file deserializes to 0 — delete viabedrockutility.json to
    // regenerate with the default.
    private double textDisplayCullDistance = 64.0;

    // Per-frame animation budget: caps how many entities run full-rate MoLang animation in a single
    // frame. The distance tiers above only throttle FAR entities; in a lobby with many entities within
    // tier1 they would all animate every frame. When the budget is exhausted, remaining entities fall
    // back to animating once every {@link #animationThrottleInterval} frames (staggered per entity), so
    // dense near-distance scenes stay bounded. 0 = unlimited (no budget).
    private int maxAnimatedEntitiesPerFrame = 24;
    // Player-specific budget pool, INDEPENDENT from the entity pool above (players and entities don't
    // preempt each other — they render in the same pass but draw from separate counters, reset together
    // once per frame in AnimationBudget.reset). Caps how many players run full-rate Bedrock setupAnim
    // per frame; the rest fall back to the {@link #animationThrottleInterval} staggered cadence. 0 =
    // unlimited. NOTE: a newly-added field absent from an existing config file deserializes to 0
    // (unlimited) — users must delete viabedrockutility.json to regenerate it with this default.
    private int maxAnimatedPlayersPerFrame = 24;
    private int animationThrottleInterval = 4;

    // Particle LOD settings (synced to ParticleManager on load/save)
    private boolean particleTickLodEnabled = true;
    private int particleTickLodNearDistance = 18;
    private int particleTickLodFarDistance = 36;

    public static LodConfig getInstance() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public Preset getPreset() {
        return preset;
    }

    public void setPreset(Preset preset) {
        this.preset = preset;
    }

    public OptimizationMode getOptimizationMode() {
        return optimizationMode;
    }

    public Preset getManualPreset() {
        return manualPreset;
    }

    public LodDetailedSettings getCustomSettings() {
        return customSettings;
    }

    public HardwareProfile getHardwareProfile() {
        if (hardwareProfile == null) {
            hardwareProfile = HardwareProfile.detect();
        }
        return hardwareProfile;
    }

    public Preset getAutomaticPreset() {
        return getHardwareProfile().recommendedPreset();
    }

    /** Applies the UI selection immediately and invalidates meshes baked under the previous policy. */
    public void applySelectionAndSave(OptimizationMode mode, Preset selectedManualPreset) {
        applySelectionAndSave(mode, selectedManualPreset, customSettings);
    }

    public void applySelectionAndSave(
            OptimizationMode mode,
            Preset selectedManualPreset,
            LodDetailedSettings selectedCustomSettings
    ) {
        optimizationMode = mode == null ? OptimizationMode.AUTO : mode;
        manualPreset = sanitizeManualPreset(selectedManualPreset);
        customSettings = (selectedCustomSettings == null
                ? LodDetailedSettings.balancedDefaults()
                : selectedCustomSettings).normalized();
        applySelectedPreset();
        save();
        FrozenEntityMeshCache.global().invalidateAll("settings_changed");
    }

    public LodTier getTier1() {
        return tier1;
    }

    public LodTier getTier2() {
        return tier2;
    }

    public LodTier getTier3() {
        return tier3;
    }

    public double getRenderCullDistance() {
        return renderCullDistance;
    }

    public boolean isFrozenMeshEnabled() {
        return frozenMeshEnabled;
    }

    public void setFrozenMeshEnabled(boolean frozenMeshEnabled) {
        this.frozenMeshEnabled = frozenMeshEnabled;
    }

    public double getFrozenMeshEnterDistance() {
        return frozenMeshEnterDistance;
    }

    public void setFrozenMeshEnterDistance(double frozenMeshEnterDistance) {
        this.frozenMeshEnterDistance = frozenMeshEnterDistance;
    }

    public double getFrozenMeshExitDistance() {
        return frozenMeshExitDistance;
    }

    public void setFrozenMeshExitDistance(double frozenMeshExitDistance) {
        this.frozenMeshExitDistance = frozenMeshExitDistance;
    }

    public long getFrozenMeshMaxGpuBytes() {
        return frozenMeshMaxGpuBytes;
    }

    public void setFrozenMeshMaxGpuBytes(long frozenMeshMaxGpuBytes) {
        this.frozenMeshMaxGpuBytes = frozenMeshMaxGpuBytes;
    }

    public void setRenderCullDistance(double renderCullDistance) {
        this.renderCullDistance = renderCullDistance;
    }

    public double getTextDisplayCullDistance() {
        return textDisplayCullDistance;
    }

    public void setTextDisplayCullDistance(double textDisplayCullDistance) {
        this.textDisplayCullDistance = textDisplayCullDistance;
    }

    public int getMaxAnimatedEntitiesPerFrame() {
        return maxAnimatedEntitiesPerFrame;
    }

    public void setMaxAnimatedEntitiesPerFrame(int maxAnimatedEntitiesPerFrame) {
        this.maxAnimatedEntitiesPerFrame = maxAnimatedEntitiesPerFrame;
    }

    public int getMaxAnimatedPlayersPerFrame() {
        return maxAnimatedPlayersPerFrame;
    }

    public void setMaxAnimatedPlayersPerFrame(int maxAnimatedPlayersPerFrame) {
        this.maxAnimatedPlayersPerFrame = maxAnimatedPlayersPerFrame;
    }

    public int getAnimationThrottleInterval() {
        return animationThrottleInterval;
    }

    public void setAnimationThrottleInterval(int animationThrottleInterval) {
        this.animationThrottleInterval = animationThrottleInterval;
    }

    public boolean isParticleTickLodEnabled() {
        return particleTickLodEnabled;
    }

    public void setParticleTickLodEnabled(boolean enabled) {
        this.particleTickLodEnabled = enabled;
    }

    public int getParticleTickLodNearDistance() {
        return particleTickLodNearDistance;
    }

    public void setParticleTickLodNearDistance(int distance) {
        this.particleTickLodNearDistance = distance;
    }

    public int getParticleTickLodFarDistance() {
        return particleTickLodFarDistance;
    }

    public void setParticleTickLodFarDistance(int distance) {
        this.particleTickLodFarDistance = distance;
    }

    /**
     * Syncs particle LOD settings to {@link net.easecation.beparticle.ParticleManager}.
     * Called after load and after each config change.
     */
    public void syncParticleSettings() {
        final var pm = net.easecation.beparticle.ParticleManager.INSTANCE;
        pm.setParticleTickLodEnabled(particleTickLodEnabled);
        pm.setParticleTickLodNearDistance((float) particleTickLodNearDistance);
        pm.setParticleTickLodFarDistance((float) particleTickLodFarDistance);
    }

    /**
     * Determines whether an entity should be completely skipped (not rendered at all)
     * based on distance. Returns true if the entity is beyond renderCullDistance.
     */
    public boolean shouldSkipRender(double distance) {
        if (preset == Preset.HIGH_QUALITY) return false;
        return renderCullDistance > 0 && distance > renderCullDistance;
    }

    /**
     * Determines whether animation should be computed this frame based on distance and frame counter.
     * Checks tiers from farthest to nearest; enabled tiers have distance > 0.
     */
    public boolean shouldAnimate(double distance, int frameCounter) {
        if (preset == Preset.HIGH_QUALITY) {
            return true;
        }

        // Check tiers from farthest to nearest
        if (tier3.distance > 0 && distance > tier3.distance) {
            return frameCounter % tier3.frameInterval == 0;
        }
        if (tier2.distance > 0 && distance > tier2.distance) {
            return frameCounter % tier2.frameInterval == 0;
        }
        if (tier1.distance > 0 && distance > tier1.distance) {
            return frameCounter % tier1.frameInterval == 0;
        }

        return true;
    }

    /**
     * Applies preset values to tier parameters.
     */
    public void applyPreset(Preset preset) {
        this.preset = preset;
        switch (preset) {
            case HIGH_QUALITY -> {
                tier1 = new LodTier(0, 1);
                tier2 = new LodTier(0, 1);
                tier3 = new LodTier(0, 1);
                renderCullDistance = 0;
                textDisplayCullDistance = 0; // never cull holograms in high-quality
                particleTickLodEnabled = false;
                maxAnimatedEntitiesPerFrame = 0; // unlimited
                maxAnimatedPlayersPerFrame = 0;  // unlimited
                animationThrottleInterval = 1;
                frozenMeshEnabled = true;
                frozenMeshEnterDistance = 32.0;
                frozenMeshExitDistance = 28.0;
                frozenMeshMaxGpuBytes = 128L * LodDetailedSettings.MIB;
            }
            case BALANCED -> {
                tier1 = new LodTier(20.0, 2);
                tier2 = new LodTier(40.0, 4);
                tier3 = new LodTier(64.0, 6);
                renderCullDistance = 64;
                textDisplayCullDistance = 80;
                particleTickLodEnabled = true;
                particleTickLodNearDistance = 20;
                particleTickLodFarDistance = 40;
                maxAnimatedEntitiesPerFrame = 32;
                maxAnimatedPlayersPerFrame = 32;
                animationThrottleInterval = 3;
                frozenMeshEnabled = true;
                frozenMeshEnterDistance = 24.0;
                frozenMeshExitDistance = 20.0;
                frozenMeshMaxGpuBytes = 128L * LodDetailedSettings.MIB;
            }
            case PERFORMANCE -> {
                tier1 = new LodTier(16.0, 3);
                tier2 = new LodTier(32.0, 5);
                tier3 = new LodTier(48.0, 8);
                renderCullDistance = 36;
                textDisplayCullDistance = 48;
                particleTickLodEnabled = true;
                particleTickLodNearDistance = 16;
                particleTickLodFarDistance = 32;
                maxAnimatedEntitiesPerFrame = 16;
                maxAnimatedPlayersPerFrame = 16;
                animationThrottleInterval = 4;
                frozenMeshEnabled = true;
                frozenMeshEnterDistance = 18.0;
                frozenMeshExitDistance = 15.0;
                frozenMeshMaxGpuBytes = 128L * LodDetailedSettings.MIB;
            }
            case EXTREME -> {
                tier1 = new LodTier(12.0, 4);
                tier2 = new LodTier(24.0, 8);
                tier3 = new LodTier(36.0, 12);
                renderCullDistance = 28;
                textDisplayCullDistance = 32;
                particleTickLodEnabled = true;
                particleTickLodNearDistance = 12;
                particleTickLodFarDistance = 24;
                maxAnimatedEntitiesPerFrame = 8;
                maxAnimatedPlayersPerFrame = 8;
                animationThrottleInterval = 6;
                frozenMeshEnabled = true;
                frozenMeshEnterDistance = 12.0;
                frozenMeshExitDistance = 9.0;
                frozenMeshMaxGpuBytes = 128L * LodDetailedSettings.MIB;
            }
            case CUSTOM -> {} // Keep current values
        }
        syncParticleSettings();
    }

    private void applySelectedPreset() {
        if (optimizationMode == null) {
            optimizationMode = OptimizationMode.AUTO;
        }
        if (manualPreset == null) {
            manualPreset = Preset.BALANCED;
        }
        Preset selected = optimizationMode == OptimizationMode.AUTO
                ? getAutomaticPreset()
                : sanitizeManualPreset(manualPreset);
        applyPreset(selected);
        if (selected == Preset.CUSTOM) {
            applyDetailedSettings(customSettings);
        }
        ViaBedrockUtilityNeoForge.LOGGER.info(
                "[Config] Optimization mode={}, effective preset={}, render-thread score={}",
                optimizationMode,
                selected,
                getHardwareProfile().performanceScore()
        );
    }

    private static Preset sanitizeManualPreset(Preset preset) {
        return preset == null ? Preset.BALANCED : preset;
    }

    private void applyDetailedSettings(LodDetailedSettings settings) {
        LodDetailedSettings value = settings.normalized();
        tier1 = new LodTier(value.tier1Distance(), value.tier1FrameInterval());
        tier2 = new LodTier(value.tier2Distance(), value.tier2FrameInterval());
        tier3 = new LodTier(value.tier3Distance(), value.tier3FrameInterval());
        renderCullDistance = value.renderCullDistance();
        textDisplayCullDistance = value.textDisplayCullDistance();
        maxAnimatedEntitiesPerFrame = value.maxAnimatedEntitiesPerFrame();
        maxAnimatedPlayersPerFrame = value.maxAnimatedPlayersPerFrame();
        animationThrottleInterval = value.animationThrottleInterval();
        frozenMeshEnabled = value.frozenMeshEnabled();
        frozenMeshEnterDistance = value.frozenMeshEnterDistance();
        frozenMeshExitDistance = value.frozenMeshExitDistance();
        frozenMeshMaxGpuBytes = value.frozenMeshMaxGpuBytes();
        particleTickLodEnabled = value.particleTickLodEnabled();
        particleTickLodNearDistance = value.particleTickLodNearDistance();
        particleTickLodFarDistance = value.particleTickLodFarDistance();
        syncParticleSettings();
    }

    private static Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve("viabedrockutility.json");
    }

    public static void load() {
        Path path = getConfigPath();
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                JsonObject source = JsonParser.parseString(json).getAsJsonObject();
                INSTANCE = GSON.fromJson(source, LodConfig.class);
                if (INSTANCE == null) {
                    INSTANCE = new LodConfig();
                }
                boolean migrated = INSTANCE.migrate(source);
                INSTANCE.applySelectedPreset();
                if (migrated) {
                    INSTANCE.save();
                }
                ViaBedrockUtilityNeoForge.LOGGER.debug("[Config] Loaded LOD config: preset={}", INSTANCE.preset);
            } catch (Exception e) {
                ViaBedrockUtilityNeoForge.LOGGER.warn("[Config] Failed to load config, using defaults", e);
                INSTANCE = new LodConfig();
            }
        } else {
            INSTANCE = new LodConfig();
            INSTANCE.applySelectedPreset();
            INSTANCE.save();
            ViaBedrockUtilityNeoForge.LOGGER.debug("[Config] Created default config file");
        }
    }

    boolean migrate(JsonObject source) {
        boolean changed = false;
        if (!source.has("optimizationMode")) {
            optimizationMode = preset == Preset.CUSTOM ? OptimizationMode.MANUAL : OptimizationMode.AUTO;
            changed = true;
        }
        if (!source.has("manualPreset")) {
            manualPreset = preset == null ? Preset.BALANCED : preset;
            changed = true;
        }
        if (!source.has("customSettings") || customSettings == null) {
            customSettings = LodDetailedSettings.from(this);
            changed = true;
        } else {
            LodDetailedSettings normalized = customSettings.normalized();
            if (!normalized.equals(customSettings)) {
                customSettings = normalized;
                changed = true;
            }
        }
        if (!source.has("frozenMeshEnterDistance") || frozenMeshEnterDistance <= 0.0) {
            frozenMeshEnterDistance = 18.0;
            changed = true;
        }
        if (!source.has("frozenMeshExitDistance") || frozenMeshExitDistance <= 0.0) {
            frozenMeshExitDistance = 15.0;
            changed = true;
        }
        if (!source.has("frozenMeshMaxGpuBytes") || frozenMeshMaxGpuBytes <= 0L) {
            frozenMeshMaxGpuBytes = 134_217_728L;
            changed = true;
        }
        if (!source.has("frozenMeshEnabled")) {
            // Existing PERFORMANCE users opted into the aggressive preset. Migrate them to the new
            // fast path; the other presets remain conservative and CUSTOM has no value to preserve.
            frozenMeshEnabled = preset == Preset.PERFORMANCE;
            changed = true;
        }
        if (frozenMeshExitDistance >= frozenMeshEnterDistance) {
            frozenMeshExitDistance = Math.max(0.0, frozenMeshEnterDistance - 3.0);
            changed = true;
        }
        if (configVersion != CURRENT_CONFIG_VERSION) {
            configVersion = CURRENT_CONFIG_VERSION;
            changed = true;
        }
        return changed;
    }

    public void save() {
        try {
            Path path = getConfigPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this));
        } catch (IOException e) {
            ViaBedrockUtilityNeoForge.LOGGER.warn("[Config] Failed to save config", e);
        }
    }
}
