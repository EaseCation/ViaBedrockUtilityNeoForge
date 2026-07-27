package org.oryxel.viabedrockutility.config;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Hardware summary whose preset score estimates CPU render-thread capability; GPU data is display-only. */
public record HardwareProfile(
        String cpuName,
        int physicalCores,
        int logicalCores,
        long maxCpuFrequencyHz,
        String gpuName,
        long gpuMemoryBytes,
        int performanceScore
) {
    private static final long GIB = 1L << 30;

    public HardwareProfile {
        cpuName = normalizeName(cpuName, "Unknown CPU");
        gpuName = normalizeName(gpuName, "Unknown GPU");
        physicalCores = Math.max(1, physicalCores);
        logicalCores = Math.max(physicalCores, logicalCores);
        maxCpuFrequencyHz = Math.max(0L, maxCpuFrequencyHz);
        gpuMemoryBytes = Math.max(0L, gpuMemoryBytes);
    }

    public static HardwareProfile detect() {
        try {
            HardwareAbstractionLayer hardware = new SystemInfo().getHardware();
            CentralProcessor processor = hardware.getProcessor();
            List<GraphicsCard> graphicsCards = hardware.getGraphicsCards();
            GraphicsCard graphics = graphicsCards.stream()
                    .max(Comparator.comparingInt(HardwareProfile::graphicsPreference))
                    .orElse(null);
            return assess(
                    processor.getProcessorIdentifier().getName(),
                    processor.getProcessorIdentifier().getMicroarchitecture(),
                    processor.getPhysicalProcessorCount(),
                    processor.getLogicalProcessorCount(),
                    processor.getMaxFreq(),
                    graphics == null ? "Unknown GPU" : graphics.getName(),
                    graphics == null ? 0L : graphics.getVRam()
            );
        } catch (RuntimeException | LinkageError ignored) {
            int processors = Runtime.getRuntime().availableProcessors();
            return assess("Unknown CPU", "unknown", Math.max(1, processors / 2), processors,
                    0L, "Unknown GPU", 0L);
        }
    }

    public static HardwareProfile assess(
            String cpuName,
            int physicalCores,
            int logicalCores,
            long maxCpuFrequencyHz,
            String gpuName,
            long gpuMemoryBytes
    ) {
        return assess(cpuName, "unknown", physicalCores, logicalCores, maxCpuFrequencyHz, gpuName, gpuMemoryBytes);
    }

    public static HardwareProfile assess(
            String cpuName,
            String microarchitecture,
            int physicalCores,
            int logicalCores,
            long maxCpuFrequencyHz,
            String gpuName,
            long gpuMemoryBytes
    ) {
        int score = CpuRenderCapability.score(cpuName, microarchitecture, physicalCores, maxCpuFrequencyHz);
        return new HardwareProfile(
                cpuName,
                physicalCores,
                logicalCores,
                maxCpuFrequencyHz,
                gpuName,
                gpuMemoryBytes,
                score
        );
    }

    public LodConfig.Preset recommendedPreset() {
        if (performanceScore >= 7) {
            return LodConfig.Preset.HIGH_QUALITY;
        }
        if (performanceScore >= 3) {
            return LodConfig.Preset.BALANCED;
        }
        if (performanceScore >= 1) {
            return LodConfig.Preset.PERFORMANCE;
        }
        return LodConfig.Preset.EXTREME;
    }

    public double maxCpuFrequencyGhz() {
        return maxCpuFrequencyHz / 1_000_000_000.0;
    }

    public int recommendedInitialRenderDistance() {
        return GpuRenderCapability.recommendedInitialRenderDistance(gpuName, gpuMemoryBytes);
    }

    private static int graphicsPreferenceScore(String gpuName, long gpuMemoryBytes) {
        String normalized = normalizeName(gpuName, "").toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "microsoft basic", "llvmpipe", "software rasterizer")) {
            return -3;
        }

        boolean integrated = isIntegrated(normalized);
        int score = integrated ? -1 : (normalized.isBlank() || normalized.contains("unknown") ? 0 : 1);
        if (containsAny(normalized, "radeon 680m", "radeon 780m", "radeon 880m", "iris xe", "arc graphics")) {
            score += 1;
        }
        if (containsAny(normalized, "geforce rtx", "radeon rx", "intel arc a", "intel arc b")) {
            score += 1;
        }
        if (!integrated) {
            if (gpuMemoryBytes >= 6 * GIB) {
                score += 2;
            } else if (gpuMemoryBytes >= 3 * GIB) {
                score += 1;
            }
        }
        return score;
    }

    private static int graphicsPreference(GraphicsCard card) {
        return graphicsPreferenceScore(card.getName(), card.getVRam()) * 100
                + (int) Math.min(99L, Math.max(0L, card.getVRam() / GIB));
    }

    private static boolean isIntegrated(String name) {
        return containsAny(
                name,
                "intel(r) hd", "intel hd", "intel(r) uhd", "intel uhd", "iris xe",
                "radeon(tm) graphics", "radeon graphics", "radeon vega", "radeon 680m",
                "radeon 780m", "radeon 880m", "apple m"
        );
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeName(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
