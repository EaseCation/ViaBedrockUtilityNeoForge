package org.oryxel.viabedrockutility.config;

import java.util.Locale;

/** Classifies only the initial vanilla render distance; it never affects the CPU-bound NPC preset. */
public final class GpuRenderCapability {
    public static final int STANDARD_RENDER_DISTANCE = 12;
    public static final int LOW_END_RENDER_DISTANCE = 8;
    private static final long GIB = 1L << 30;

    private GpuRenderCapability() {
    }

    public static int recommendedInitialRenderDistance(String gpuName, long gpuMemoryBytes) {
        return isLowPerformance(gpuName, gpuMemoryBytes)
                ? LOW_END_RENDER_DISTANCE
                : STANDARD_RENDER_DISTANCE;
    }

    public static boolean isLowPerformance(String gpuName, long gpuMemoryBytes) {
        String name = normalize(gpuName);
        if (name.isBlank() || name.contains("unknown")) {
            return false;
        }
        if (containsAny(name, "microsoft basic", "llvmpipe", "software rasterizer")) {
            return true;
        }

        // Modern integrated GPUs are capable of the default 12 chunks despite reporting little dedicated VRAM.
        if (containsAny(name, "iris xe", "iris(r) xe", "intel arc", "radeon 660m", "radeon 680m", "radeon 760m",
                "radeon 780m", "radeon 860m", "radeon 880m", "apple m")) {
            return false;
        }
        if (containsAny(name, "geforce rtx", "radeon rx", "geforce gtx")) {
            return gpuMemoryBytes > 0 && gpuMemoryBytes < 2 * GIB;
        }

        if (containsAny(name, "intel(r) hd", "intel hd", "intel(r) uhd", "intel uhd",
                "radeon(tm) graphics", "radeon graphics", "radeon vega", "radeon r5", "radeon r7",
                "geforce gt ", "geforce mx")) {
            return true;
        }
        return gpuMemoryBytes > 0 && gpuMemoryBytes < 2 * GIB;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
