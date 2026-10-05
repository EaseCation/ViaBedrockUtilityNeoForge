package org.oryxel.viabedrockutility.entity;

import net.minecraft.util.Mth;

/**
 * 基岩 ActorWalkAnimationComponent 的运动状态，客户端 tick 推进，渲染只读取插值结果。
 * 数值和计算顺序来自网易 3.10 原生实现，证据见 docs/bedrock-motion-queries.md。
 */
public final class BedrockEntityMotion {
    private static final float TICKS_PER_SECOND = 20.0F;
    private static final float MOVEMENT_FACTOR = 1.6F;
    private static final float MOVEMENT_LIMIT = 0.4F;
    private static final float TURN_FACTOR = 0.02F;
    private static final float TURN_LIMIT = 0.2F;
    private static final float PREVIOUS_SPEED_FACTOR = 0.6F;

    private boolean initialized;
    private int lastTick;
    private double lastX;
    private double lastY;
    private double lastZ;
    private float lastBodyYaw;
    private float previousWalkSpeed;
    private float walkSpeed;
    private float walkDistance;
    private float groundSpeed;
    private float verticalSpeed;

    public void initialize(int tick, double x, double y, double z, float bodyYaw) {
        if (!initialized && finitePosition(x, y, z, bodyYaw)) {
            initialized = true;
            lastTick = tick;
            lastX = x;
            lastY = y;
            lastZ = z;
            lastBodyYaw = bodyYaw;
        }
    }

    public void tick(int tick, double x, double y, double z, float bodyYaw, boolean riding) {
        if (!finitePosition(x, y, z, bodyYaw)) {
            return;
        }
        if (!initialized || tick < lastTick) {
            reset();
            initialize(tick, x, y, z, bodyYaw);
            return;
        }
        if (tick == lastTick) {
            return;
        }

        final double dx = x - lastX;
        final double dy = y - lastY;
        final double dz = z - lastZ;
        final float horizontalDistance = (float) Math.sqrt(dx * dx + dz * dz);
        // ground_speed 的原生实现读取 PostTickPositionDeltaComponent 的三维长度。
        groundSpeed = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) * TICKS_PER_SECOND;
        verticalSpeed = (float) dy * TICKS_PER_SECOND;

        if (riding) {
            previousWalkSpeed = 0.0F;
            walkSpeed = 0.0F;
        } else {
            previousWalkSpeed = walkSpeed;
            final float target = horizontalDistance == 0.0F
                    ? Math.min(Math.abs(Mth.wrapDegrees(bodyYaw - lastBodyYaw)) * TURN_FACTOR, TURN_LIMIT)
                    : Math.min(horizontalDistance * MOVEMENT_FACTOR, MOVEMENT_LIMIT);
            walkSpeed = Math.fma(previousWalkSpeed, PREVIOUS_SPEED_FACTOR, target);
            walkDistance += walkSpeed;
        }

        lastTick = tick;
        lastX = x;
        lastY = y;
        lastZ = z;
        lastBodyYaw = bodyYaw;
    }

    public float modifiedMoveSpeed(float partialTick, boolean baby) {
        final float alpha = frameAlpha(partialTick);
        final float interpolated = Math.fma(walkSpeed - previousWalkSpeed, alpha, previousWalkSpeed);
        // BABY 倍率位于上限处理之后；不能把全部动画权重额外限制到 [0, 1]。
        return Math.min(interpolated, 1.0F) * (baby ? 1.5F : 1.0F);
    }

    public float modifiedDistanceMoved(float partialTick) {
        // 原生 FNMSUB 的结果为 speed * alpha - speed，再加累计动画距离。
        return Math.fma(frameAlpha(partialTick) - 1.0F, walkSpeed, walkDistance);
    }

    public float groundSpeed() {
        return groundSpeed;
    }

    public float verticalSpeed() {
        return verticalSpeed;
    }

    private void reset() {
        initialized = false;
        previousWalkSpeed = 0.0F;
        walkSpeed = 0.0F;
        walkDistance = 0.0F;
        groundSpeed = 0.0F;
        verticalSpeed = 0.0F;
    }

    private static float frameAlpha(float partialTick) {
        return Float.isFinite(partialTick) ? Mth.clamp(partialTick, 0.0F, 1.0F) : 0.0F;
    }

    private static boolean finitePosition(double x, double y, double z, float bodyYaw) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Float.isFinite(bodyYaw);
    }
}
