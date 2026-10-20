package org.oryxel.viabedrockutility.payload.impl.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.oryxel.viabedrockutility.payload.BasePayload;

import java.util.UUID;

/** 代理确认自定义宿主或基岩头颅移动能力，并保留对应的明确瞬移。 */
@Getter
@RequiredArgsConstructor
public final class CustomEntityMotionPayload extends BasePayload {
    public static final int ENABLE = 0;
    public static final int TELEPORT = 1;
    public static final int ENABLE_WITHER_SKULLS = 2;
    public static final int TELEPORT_WITHER_SKULL = 3;
    public static final int TELEPORT_BYTES = 2 * Long.BYTES + 3 * Double.BYTES + 2 * Float.BYTES + 1;

    private final int action;
    private final UUID uuid;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final boolean onGround;

    public static CustomEntityMotionPayload enable() {
        return new CustomEntityMotionPayload(ENABLE, null, 0, 0, 0, 0, 0, false);
    }

    public static CustomEntityMotionPayload enableWitherSkulls() {
        return new CustomEntityMotionPayload(ENABLE_WITHER_SKULLS, null, 0, 0, 0, 0, 0, false);
    }
}
