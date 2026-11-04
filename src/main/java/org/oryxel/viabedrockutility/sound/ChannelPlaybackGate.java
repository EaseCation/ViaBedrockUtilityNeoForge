package org.oryxel.viabedrockutility.sound;

/** Native Channel mixin bridge. Every engine assignment resets the gate, including ordinary sounds. */
public interface ChannelPlaybackGate {
    void viabedrockutility$playbackGate(CancellableSoundInstance owner);
}
