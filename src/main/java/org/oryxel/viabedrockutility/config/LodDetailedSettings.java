package org.oryxel.viabedrockutility.config;

/** Complete user-editable LOD policy retained separately from automatic preset values. */
public record LodDetailedSettings(
        double tier1Distance,
        int tier1FrameInterval,
        double tier2Distance,
        int tier2FrameInterval,
        double tier3Distance,
        int tier3FrameInterval,
        double renderCullDistance,
        double textDisplayCullDistance,
        int maxAnimatedEntitiesPerFrame,
        int animationThrottleInterval,
        boolean frozenMeshEnabled,
        double frozenMeshEnterDistance,
        double frozenMeshExitDistance,
        long frozenMeshMaxGpuBytes,
        boolean particleTickLodEnabled,
        int particleTickLodNearDistance,
        int particleTickLodFarDistance
) {
    public static final long MIB = 1L << 20;

    public LodDetailedSettings normalized() {
        double enterDistance = clamp(frozenMeshEnterDistance, 12.0, 128.0);
        double exitDistance = clamp(frozenMeshExitDistance, 6.0, enterDistance - 1.0);
        int particleNear = clamp(particleTickLodNearDistance, 1, 256);
        int particleFar = clamp(particleTickLodFarDistance, particleNear, 256);
        return new LodDetailedSettings(
                normalizeTierDistance(tier1Distance),
                clamp(tier1FrameInterval, 1, 60),
                normalizeTierDistance(tier2Distance),
                clamp(tier2FrameInterval, 1, 60),
                normalizeTierDistance(tier3Distance),
                clamp(tier3FrameInterval, 1, 60),
                clamp(renderCullDistance, 0.0, 512.0),
                clamp(textDisplayCullDistance, 0.0, 512.0),
                clamp(maxAnimatedEntitiesPerFrame, 0, 512),
                clamp(animationThrottleInterval, 1, 60),
                frozenMeshEnabled,
                enterDistance,
                exitDistance,
                clamp(frozenMeshMaxGpuBytes, 16 * MIB, 512 * MIB),
                particleTickLodEnabled,
                particleNear,
                particleFar
        );
    }

    public static LodDetailedSettings from(LodConfig config) {
        return new LodDetailedSettings(
                config.getTier1().distance,
                config.getTier1().frameInterval,
                config.getTier2().distance,
                config.getTier2().frameInterval,
                config.getTier3().distance,
                config.getTier3().frameInterval,
                config.getRenderCullDistance(),
                config.getTextDisplayCullDistance(),
                config.getMaxAnimatedEntitiesPerFrame(),
                config.getAnimationThrottleInterval(),
                config.isFrozenMeshEnabled(),
                config.getFrozenMeshEnterDistance(),
                config.getFrozenMeshExitDistance(),
                config.getFrozenMeshMaxGpuBytes(),
                config.isParticleTickLodEnabled(),
                config.getParticleTickLodNearDistance(),
                config.getParticleTickLodFarDistance()
        ).normalized();
    }

    public static LodDetailedSettings balancedDefaults() {
        return new LodDetailedSettings(
                20.0, 2,
                40.0, 4,
                64.0, 6,
                64.0,
                80.0,
                32,
                3,
                true,
                24.0,
                20.0,
                128 * MIB,
                true,
                20,
                40
        );
    }

    private static double normalizeTierDistance(double distance) {
        return distance <= 0.0 ? 0.0 : clamp(distance, 4.0, 512.0);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
