package org.oryxel.viabedrockutility.renderer;

import org.oryxel.viabedrockutility.config.LodConfig;

/**
 * Bounds CPU mesh expansion and GPU uploads for animated entity poses.
 *
 * <p>The rotating window prevents stable entity render order from granting every frame to the same
 * entities. A renderer consumes at most one slot and may rebuild all of its model layers with it.
 * Render-thread confined.
 */
public final class PoseMeshRebuildBudget {
    private static boolean unlimited = true;
    private static int limit = Integer.MAX_VALUE;
    private static int requestCount;
    private static int grantedCount;
    private static int previousRequestCount;
    private static int windowStart;

    private PoseMeshRebuildBudget() {
    }

    public static void reset() {
        int animationLimit = LodConfig.getInstance().getMaxAnimatedEntitiesPerFrame();
        // Mesh expansion is substantially more expensive than evaluating one entity pose. Keep it
        // below the animation budget while retaining an unlimited high-quality mode.
        reset(animationLimit <= 0 ? 0 : Math.max(1, animationLimit / 2));
    }

    static void reset(int maxRebuilds) {
        unlimited = maxRebuilds <= 0;
        limit = unlimited ? Integer.MAX_VALUE : maxRebuilds;
        if (!unlimited && previousRequestCount > 0) {
            windowStart = Math.floorMod(windowStart + Math.min(limit, previousRequestCount),
                    previousRequestCount);
        } else {
            windowStart = 0;
        }
        requestCount = 0;
        grantedCount = 0;
    }

    public static boolean tryAcquire() {
        int requestIndex = requestCount++;
        if (unlimited) {
            return true;
        }
        if (grantedCount >= limit) {
            return false;
        }

        int population = Math.max(previousRequestCount, limit);
        int distance = requestIndex - windowStart;
        if (distance < 0) {
            distance += population;
        }
        if (distance >= 0 && distance < limit) {
            grantedCount++;
            return true;
        }
        return false;
    }

    public static void endFrame() {
        previousRequestCount = requestCount;
        if (previousRequestCount == 0) {
            windowStart = 0;
        } else if (windowStart >= previousRequestCount) {
            windowStart %= previousRequestCount;
        }
    }

    static void clearForTesting() {
        unlimited = true;
        limit = Integer.MAX_VALUE;
        requestCount = 0;
        grantedCount = 0;
        previousRequestCount = 0;
        windowStart = 0;
    }
}
