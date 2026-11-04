package org.oryxel.viabedrockutility.mixin.impl.sound;

import com.mojang.blaze3d.audio.Channel;
import org.oryxel.viabedrockutility.sound.CancellableSoundInstance;
import org.oryxel.viabedrockutility.sound.ChannelPlaybackGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Channel.class)
public abstract class ChannelMixin implements ChannelPlaybackGate {
    @Unique private CancellableSoundInstance viabedrockutility$playbackOwner;

    @Override public void viabedrockutility$playbackGate(CancellableSoundInstance owner) {
        viabedrockutility$playbackOwner = owner;
    }

    @Inject(method = {"play", "unpause"}, at = @At("HEAD"), cancellable = true)
    private void viabedrockutility$cancelledStart(CallbackInfo callback) {
        if (viabedrockutility$playbackOwner != null && viabedrockutility$playbackOwner.isPlaybackCancelled()) {
            // Decoding may just have attached a buffer to a never-started source. Mark it stopped
            // as well, so the vanilla channel pool can retire it instead of retaining INITIAL.
            ((Channel) (Object) this).stop();
            callback.cancel();
        }
    }

    @Inject(method = "stopped", at = @At("HEAD"), cancellable = true)
    private void viabedrockutility$cancelledChannel(CallbackInfoReturnable<Boolean> callback) {
        // OpenAL stop on a never-started source can leave it INITIAL, not STOPPED. The vanilla
        // pool must still release this terminally cancelled lease, even before decoding finishes.
        if (viabedrockutility$playbackOwner != null && viabedrockutility$playbackOwner.isPlaybackCancelled()) callback.setReturnValue(true);
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void viabedrockutility$releaseOwner(CallbackInfo callback) {
        viabedrockutility$playbackOwner = null;
    }
}
