package org.oryxel.viabedrockutility.payload.impl.camera;

import nakern.be_camera.camera.*;
import nakern.be_camera.easings.Easings;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles camera payloads by calling BECamera API.
 */
public class CameraPayloadHandler {

    public void handle(CameraPayload payload) {
        if (payload instanceof CameraInstructionPayload p) {
            handleInstruction(p);
        } else if (payload instanceof CameraShakePayload p) {
            handleShake(p);
        } else if (payload instanceof CameraPresetsPayload p) {
            handlePresets(p);
        }
        // CONFIRM type is handled in CameraPayload.STREAM_CODEC
    }

    private void handleInstruction(CameraInstructionPayload p) {
        if (p.isHasSet()) {
            // Resolve preset with parent inheritance
            final CameraPresetManager.Preset resolvedPreset = CameraPresetManager.INSTANCE.resolvePreset(p.getPresetRuntimeId());

            // Resolve position: prefer explicit position, fall back to preset
            Vec3 position = null;
            Vec2 rotation = null;
            Vec3 facing = null;

            if (p.isHasPos()) {
                position = new Vec3(p.getPosX(), p.getPosY(), p.getPosZ());
            } else if (resolvedPreset != null && resolvedPreset.getPosX() != null && resolvedPreset.getPosY() != null && resolvedPreset.getPosZ() != null) {
                position = new Vec3(resolvedPreset.getPosX(), resolvedPreset.getPosY(), resolvedPreset.getPosZ());
            }

            if (p.isHasRot()) {
                rotation = new Vec2(p.getRotX(), p.getRotY());
            } else if (resolvedPreset != null && resolvedPreset.getRotX() != null && resolvedPreset.getRotY() != null) {
                rotation = new Vec2(resolvedPreset.getRotX(), resolvedPreset.getRotY());
            }

            if (p.isHasFacing()) {
                facing = new Vec3(p.getFacingX(), p.getFacingY(), p.getFacingZ());
            }

            if (position == null) {
                if (p.isDefault()) {
                    // Default preset without position (e.g., first_person/third_person) 閳?clear camera override
                    CameraManager.INSTANCE.clear();
                    ViaBedrockUtilityNeoForge.LOGGER.debug("[BECamera] Camera set to default preset {} (no position), clearing override", p.getPresetRuntimeId());
                } else {
                    ViaBedrockUtilityNeoForge.LOGGER.warn("[BECamera] Camera set instruction has no position (preset {} may not define position)", p.getPresetRuntimeId());
                }
            } else {
                EaseOptions easeOptions = null;
                if (p.isHasEase()) {
                    easeOptions = new EaseOptions(
                            Easings.byBedrockOrdinal(p.getEasingType()),
                            (long) (p.getEaseDuration() * 1000)
                    );
                }

                CameraData data = new CameraData(position, facing, rotation, easeOptions);
                CameraManager.INSTANCE.setCamera(data);

                // Update playerEffects state from resolved preset
                boolean effectsEnabled = resolvedPreset != null
                        && resolvedPreset.getPlayerEffects() != null
                        && resolvedPreset.getPlayerEffects();
                CameraManager.INSTANCE.setPlayerEffects(effectsEnabled);

                ViaBedrockUtilityNeoForge.LOGGER.info("[BECamera] Camera set: pos={} rot=(pitch={}, yaw={}) facing={} ease={} playerEffects={}",
                        position, rotation != null ? rotation.x : "null", rotation != null ? rotation.y : "null", facing, p.isHasEase(), effectsEnabled);
            }
        }

        if (p.isHasClear()) {
            CameraManager.INSTANCE.clear();
            ViaBedrockUtilityNeoForge.LOGGER.debug("[BECamera] Camera cleared");
        }

        if (p.isHasFade()) {
            float fadeIn = p.isHasFadeTime() ? p.getFadeIn() : 0;
            float fadeStay = p.isHasFadeTime() ? p.getFadeStay() : 0;
            float fadeOut = p.isHasFadeTime() ? p.getFadeOut() : 0;

            int r = 0, g = 0, b = 0;
            if (p.isHasFadeColor()) {
                r = Math.round(p.getFadeR() * 255);
                g = Math.round(p.getFadeG() * 255);
                b = Math.round(p.getFadeB() * 255);
            }

            int color = (r << 16) | (g << 8) | b;
            CameraFadeOptions fadeOptions = new CameraFadeOptions(
                    color,
                    (long) (fadeIn * 1000),
                    (long) (fadeStay * 1000),
                    (long) (fadeOut * 1000)
            );
            CameraManager.INSTANCE.fade(fadeOptions);
            ViaBedrockUtilityNeoForge.LOGGER.debug("[BECamera] Camera fade: in={}s stay={}s out={}s color=#{}", fadeIn, fadeStay, fadeOut, Integer.toHexString(color));
        }
    }

    private void handleShake(CameraShakePayload p) {
        if (p.getAction() == 0) {
            // Add shake
            CameraShakeManager.INSTANCE.addShake(p.getIntensity(), p.getDuration(), p.getShakeType());
            ViaBedrockUtilityNeoForge.LOGGER.debug("[BECamera] Camera shake added: intensity={} duration={} type={}", p.getIntensity(), p.getDuration(), p.getShakeType());
        } else {
            // Stop all shakes
            CameraShakeManager.INSTANCE.stopAll();
            ViaBedrockUtilityNeoForge.LOGGER.debug("[BECamera] Camera shake stopped");
        }
    }

    private void handlePresets(CameraPresetsPayload p) {
        List<CameraPresetManager.Preset> presets = new ArrayList<>();
        for (CameraPresetsPayload.PresetEntry entry : p.getPresets()) {
            presets.add(new CameraPresetManager.Preset(
                    entry.getName(),
                    entry.getParent().isEmpty() ? null : entry.getParent(),
                    entry.getPosX(), entry.getPosY(), entry.getPosZ(),
                    entry.getRotX(), entry.getRotY(),
                    entry.getPlayerEffects(),
                    entry.getAudioListener()
            ));
        }
        CameraPresetManager.INSTANCE.setPresets(presets);
        ViaBedrockUtilityNeoForge.LOGGER.debug("[BECamera] Camera presets loaded: {} presets", presets.size());
    }
}
