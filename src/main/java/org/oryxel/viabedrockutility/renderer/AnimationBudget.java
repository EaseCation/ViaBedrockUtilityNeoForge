package org.oryxel.viabedrockutility.renderer;

import org.oryxel.viabedrockutility.config.LodConfig;

/**
 * Per-frame budget that caps how many custom entities run full-rate MoLang animation in a single frame.
 *
 * <p>The distance LOD ({@link LodConfig#shouldAnimate}) only throttles far entities; a lobby packed with
 * entities within tier1 would otherwise animate every one of them every frame (the allocation storm JFR
 * traced to {@code MoLangEngine.eval}). {@link #reset()} is called once per frame before entities render
 * (RenderLevelStageEvent.AfterOpaqueBlocks); each entity that wants to animate calls {@link #tryAcquire()}.
 * Entities that exhaust the budget fall back to a staggered every-N-frames cadence at the call site.
 *
 * <p>Two INDEPENDENT pools are tracked: custom entities ({@link #tryAcquire()}) and players
 * ({@link #tryAcquirePlayer()}). They render in the same pass but draw from separate counters, so a crowd
 * of custom NPCs never starves player animation and vice versa. Both are refilled together in {@link #reset()}.
 *
 * <p>Render-thread confined - no synchronization needed.
 */
public final class AnimationBudget {

    private static boolean unlimited = true;
    private static int remaining = Integer.MAX_VALUE;

    private static boolean unlimitedPlayers = true;
    private static int remainingPlayers = Integer.MAX_VALUE;

    private AnimationBudget() {
    }

    /** Reset both budgets for a new frame from the active LOD config. */
    public static void reset() {
        final int max = LodConfig.getInstance().getMaxAnimatedEntitiesPerFrame();
        if (max <= 0) {
            unlimited = true;
            remaining = Integer.MAX_VALUE;
        } else {
            unlimited = false;
            remaining = max;
        }

        final int maxPlayers = LodConfig.getInstance().getMaxAnimatedPlayersPerFrame();
        if (maxPlayers <= 0) {
            unlimitedPlayers = true;
            remainingPlayers = Integer.MAX_VALUE;
        } else {
            unlimitedPlayers = false;
            remainingPlayers = maxPlayers;
        }
    }

    /** @return true if this entity may animate at full rate this frame (consuming one entity-pool slot). */
    public static boolean tryAcquire() {
        if (unlimited) {
            return true;
        }
        if (remaining > 0) {
            remaining--;
            return true;
        }
        return false;
    }

    /** @return true if this player may animate at full rate this frame (consuming one player-pool slot). */
    public static boolean tryAcquirePlayer() {
        if (unlimitedPlayers) {
            return true;
        }
        if (remainingPlayers > 0) {
            remainingPlayers--;
            return true;
        }
        return false;
    }
}
