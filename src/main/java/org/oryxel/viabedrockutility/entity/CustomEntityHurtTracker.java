package org.oryxel.viabedrockutility.entity;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.util.UUID;

/** 保存客户端 tick 驱动的短时受击状态；服务器世界时间同步不参与计时。 */
public final class CustomEntityHurtTracker {
    private static final int HURT_DURATION_TICKS = 10;
    private final Object2LongMap<UUID> hurtStartedAt = new Object2LongOpenHashMap<>();
    private Object levelIdentity;
    private long tick;

    public synchronized long currentTick(Object level) {
        if (level != levelIdentity) {
            clearAll();
            levelIdentity = level;
        }
        return tick;
    }

    /** 同一世界每个未暂停的客户端 tick 只由 ClientTickEvent.Post 推进一次。 */
    public synchronized void advanceTick(Object level, boolean paused) {
        currentTick(level);
        if (level != null && !paused) {
            tick++;
            prune(tick);
        }
    }

    public synchronized void markHurt(UUID uuid, long currentTick) {
        hurtStartedAt.put(uuid, currentTick);
    }

    public synchronized boolean isHurt(UUID uuid, long currentTick) {
        if (!hurtStartedAt.containsKey(uuid)) {
            return false;
        }
        if (isActive(hurtStartedAt.getLong(uuid), currentTick)) {
            return true;
        }
        hurtStartedAt.removeLong(uuid);
        return false;
    }

    /** 即使实体没有被渲染，也在客户端 tick 清理已经到期或时间回退的条目。 */
    public synchronized void prune(long currentTick) {
        hurtStartedAt.object2LongEntrySet().removeIf(entry -> !isActive(entry.getLongValue(), currentTick));
    }

    public synchronized void clear(UUID uuid) {
        hurtStartedAt.removeLong(uuid);
    }

    public synchronized void clearAll() {
        hurtStartedAt.clear();
        levelIdentity = null;
        tick = 0L;
    }

    private static boolean isActive(long startedAt, long currentTick) {
        return currentTick >= startedAt && currentTick - startedAt < HURT_DURATION_TICKS;
    }
}
