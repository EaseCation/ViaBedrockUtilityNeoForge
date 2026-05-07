package org.oryxel.viabedrockutility.payload.impl.camera;

import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;

@Getter
public final class CameraShakePayload extends CameraPayload {

    private final float intensity;
    private final float duration;
    private final byte shakeType; // 0 = Positional, 1 = Rotational
    private final byte action;    // 0 = Add, 1 = Stop

    private CameraShakePayload(float intensity, float duration, byte shakeType, byte action) {
        super(CameraPayloadType.CAMERA_SHAKE);
        this.intensity = intensity;
        this.duration = duration;
        this.shakeType = shakeType;
        this.action = action;
    }

    public static CameraShakePayload decode(FriendlyByteBuf buf) {
        float intensity = buf.readFloat();
        float duration = buf.readFloat();
        byte shakeType = buf.readByte();
        byte action = buf.readByte();
        return new CameraShakePayload(intensity, duration, shakeType, action);
    }
}
