package org.oryxel.viabedrockutility.payload.impl.camera;

import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;

@Getter
public final class CameraInstructionPayload extends CameraPayload {

    // Set instruction
    private final boolean hasSet;
    private final int presetRuntimeId;
    private final boolean hasEase;
    private final byte easingType;
    private final float easeDuration;
    private final boolean hasPos;
    private final float posX, posY, posZ;
    private final boolean hasRot;
    private final float rotX, rotY;
    private final boolean hasFacing;
    private final float facingX, facingY, facingZ;
    private final boolean isDefault;

    // Clear instruction
    private final boolean hasClear;

    // Fade instruction
    private final boolean hasFade;
    private final boolean hasFadeTime;
    private final float fadeIn, fadeStay, fadeOut;
    private final boolean hasFadeColor;
    private final float fadeR, fadeG, fadeB;

    private CameraInstructionPayload(
            boolean hasSet, int presetRuntimeId,
            boolean hasEase, byte easingType, float easeDuration,
            boolean hasPos, float posX, float posY, float posZ,
            boolean hasRot, float rotX, float rotY,
            boolean hasFacing, float facingX, float facingY, float facingZ,
            boolean isDefault,
            boolean hasClear,
            boolean hasFade, boolean hasFadeTime,
            float fadeIn, float fadeStay, float fadeOut,
            boolean hasFadeColor, float fadeR, float fadeG, float fadeB) {
        super(CameraPayloadType.CAMERA_INSTRUCTION);
        this.hasSet = hasSet;
        this.presetRuntimeId = presetRuntimeId;
        this.hasEase = hasEase;
        this.easingType = easingType;
        this.easeDuration = easeDuration;
        this.hasPos = hasPos;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.hasRot = hasRot;
        this.rotX = rotX;
        this.rotY = rotY;
        this.hasFacing = hasFacing;
        this.facingX = facingX;
        this.facingY = facingY;
        this.facingZ = facingZ;
        this.isDefault = isDefault;
        this.hasClear = hasClear;
        this.hasFade = hasFade;
        this.hasFadeTime = hasFadeTime;
        this.fadeIn = fadeIn;
        this.fadeStay = fadeStay;
        this.fadeOut = fadeOut;
        this.hasFadeColor = hasFadeColor;
        this.fadeR = fadeR;
        this.fadeG = fadeG;
        this.fadeB = fadeB;
    }

    public static CameraInstructionPayload decode(FriendlyByteBuf buf) {
        boolean hasSet = buf.readBoolean();
        boolean hasClear = buf.readBoolean();
        boolean hasFade = buf.readBoolean();

        int presetRuntimeId = 0;
        boolean hasEase = false;
        byte easingType = 0;
        float easeDuration = 0;
        boolean hasPos = false;
        float posX = 0, posY = 0, posZ = 0;
        boolean hasRot = false;
        float rotX = 0, rotY = 0;
        boolean hasFacing = false;
        float facingX = 0, facingY = 0, facingZ = 0;
        boolean isDefault = false;

        if (hasSet) {
            presetRuntimeId = buf.readInt();
            hasEase = buf.readBoolean();
            if (hasEase) {
                easingType = buf.readByte();
                easeDuration = buf.readFloat();
            }
            hasPos = buf.readBoolean();
            if (hasPos) {
                posX = buf.readFloat();
                posY = buf.readFloat();
                posZ = buf.readFloat();
            }
            hasRot = buf.readBoolean();
            if (hasRot) {
                rotX = buf.readFloat();
                rotY = buf.readFloat();
            }
            hasFacing = buf.readBoolean();
            if (hasFacing) {
                facingX = buf.readFloat();
                facingY = buf.readFloat();
                facingZ = buf.readFloat();
            }
            isDefault = buf.readBoolean();
        }

        boolean hasFadeTime = false;
        float fadeIn = 0, fadeStay = 0, fadeOut = 0;
        boolean hasFadeColor = false;
        float fadeR = 0, fadeG = 0, fadeB = 0;

        if (hasFade) {
            hasFadeTime = buf.readBoolean();
            if (hasFadeTime) {
                fadeIn = buf.readFloat();
                fadeStay = buf.readFloat();
                fadeOut = buf.readFloat();
            }
            hasFadeColor = buf.readBoolean();
            if (hasFadeColor) {
                fadeR = buf.readFloat();
                fadeG = buf.readFloat();
                fadeB = buf.readFloat();
            }
        }

        return new CameraInstructionPayload(
                hasSet, presetRuntimeId,
                hasEase, easingType, easeDuration,
                hasPos, posX, posY, posZ,
                hasRot, rotX, rotY,
                hasFacing, facingX, facingY, facingZ,
                isDefault,
                hasClear,
                hasFade, hasFadeTime, fadeIn, fadeStay, fadeOut,
                hasFadeColor, fadeR, fadeG, fadeB);
    }
}
