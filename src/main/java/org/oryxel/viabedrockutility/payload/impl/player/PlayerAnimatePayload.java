package org.oryxel.viabedrockutility.payload.impl.player;

import lombok.Getter;
import org.oryxel.viabedrockutility.payload.BasePayload;

import java.util.UUID;

/** 保留基岩 AnimateEntity 的玩家动画控制器参数。 */
@Getter
public final class PlayerAnimatePayload extends BasePayload {
    private final UUID playerUuid;
    private final String animation;
    private final String nextState;
    private final String stopExpression;
    private final int stopExpressionVersion;
    private final String controller;
    private final float blendOutTime;

    public PlayerAnimatePayload(UUID playerUuid, String animation, String nextState,
                                String stopExpression, int stopExpressionVersion,
                                String controller, float blendOutTime) {
        this.playerUuid = playerUuid;
        this.animation = animation;
        this.nextState = nextState;
        this.stopExpression = stopExpression;
        this.stopExpressionVersion = stopExpressionVersion;
        this.controller = controller;
        this.blendOutTime = blendOutTime;
    }
}
