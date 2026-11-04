package org.oryxel.viabedrockutility.mixin.impl.sound;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.oryxel.viabedrockutility.sound.CancellableSoundInstance;
import org.oryxel.viabedrockutility.sound.ChannelPlaybackGate;
import java.util.function.Consumer;

@Mixin(SoundEngine.class)
public class SoundSystemMixin {

    // Configure ownership on the same executor, before either cached or asynchronously decoded
    // buffers can call Channel.play. Checking only when scheduling the callback leaves a race.
    @WrapOperation(method = "play", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V"))
    private void viabedrockutility$bindPlayback(ChannelAccess.ChannelHandle handle, Consumer<Channel> configure,
                                              Operation<Void> original, @Local(argsOnly = true) SoundInstance sound) {
        original.call(handle, (Consumer<Channel>) channel -> {
            ((ChannelPlaybackGate) channel).viabedrockutility$playbackGate(
                    sound instanceof CancellableSoundInstance managed ? managed : null);
            configure.accept(channel);
        });
    }

    /**
     * Removes the [0.5, 2.0] pitch clamp to support Bedrock Edition's
     * extended pitch range (0.0-256.0).
     * OpenAL natively supports arbitrary positive pitch values.
     */
    @Redirect(
            method = "calculatePitch",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;clamp(FFF)F")
    )
    private float removePitchClamp(float value, float min, float max) {
        return Math.max(value, 0.0f);
    }
}
