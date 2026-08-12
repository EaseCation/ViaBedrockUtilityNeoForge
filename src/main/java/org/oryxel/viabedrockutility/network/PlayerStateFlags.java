package org.oryxel.viabedrockutility.network;

public final class PlayerStateFlags {

    public static final int CRAWLING = 1 << 0;
    public static final int SNEAKING = 1 << 1;
    public static final int SPRINTING = 1 << 2;
    public static final int SWIMMING = 1 << 3;
    public static final int FALL_FLYING = 1 << 4;
    public static final int FLYING = 1 << 5;

    private PlayerStateFlags() {
    }

    public static int create(final boolean crawling, final boolean sneaking, final boolean sprinting,
                             final boolean swimming, final boolean fallFlying, final boolean flying) {
        int flags = 0;
        flags = set(flags, CRAWLING, crawling);
        flags = set(flags, SNEAKING, sneaking);
        flags = set(flags, SPRINTING, sprinting);
        flags = set(flags, SWIMMING, swimming);
        flags = set(flags, FALL_FLYING, fallFlying);
        return set(flags, FLYING, flying);
    }

    public static boolean isCrawling(final boolean visuallySwimming, final boolean inWater,
                                     final boolean inSwimmableFluid) {
        return visuallySwimming && !inWater && !inSwimmableFluid;
    }

    private static int set(final int flags, final int flag, final boolean enabled) {
        return enabled ? flags | flag : flags;
    }
}
