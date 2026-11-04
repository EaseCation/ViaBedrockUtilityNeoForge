package org.oryxel.viabedrockutility.sound;

import net.minecraft.client.resources.sounds.SoundInstance;

/** Opt-in to cancellation across asynchronous decoding/native start.
 * Called on the audio executor: implementations must read a thread-safe flag, not world state.
 * Cancellation is terminal for one SoundInstance; a new playback requires a new instance.
 */
public interface CancellableSoundInstance extends SoundInstance {
    boolean isPlaybackCancelled();
}
