package org.oryxel.viabedrockutility.animation;

/** 所有实体和渲染 pass 共用同一份实际帧耗时，不以 tick 时长或实体绘制次数替代。 */
public final class BedrockFrameTime {
    public static final BedrockFrameTime INSTANCE = new BedrockFrameTime();

    private boolean initialized;
    private long previousNanos;
    private float deltaSeconds;

    public void beginFrame(long nowNanos) {
        deltaSeconds = initialized ? Math.max(0L, nowNanos - previousNanos) / 1_000_000_000.0F : 0.0F;
        previousNanos = nowNanos;
        initialized = true;
    }

    public float deltaSeconds() {
        return deltaSeconds;
    }
}
