package org.oryxel.viabedrockutility.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Plays aliases registered by ViaBedrock's generated {@code assets/bedrock/sounds.json}. */
public final class BedrockJavaSoundPlayer {
    private static final Set<ResourceLocation> WARNED_MISSING = ConcurrentHashMap.newKeySet();

    private BedrockJavaSoundPlayer() {
    }

    public static boolean playAt(String identifier, double x, double y, double z,
                                 SoundSource source, float volume, float pitch) {
        final ResourceLocation event = resolveEvent(identifier);
        final Minecraft minecraft = Minecraft.getInstance();
        if (event == null || minecraft.level == null || !isRegistered(minecraft, event)) return false;
        minecraft.level.playLocalSound(x, y, z, SoundEvent.createVariableRangeEvent(event),
                source, volume, pitch, false);
        return true;
    }

    public static boolean playRelative(String identifier, SoundSource source, float volume, float pitch) {
        final ResourceLocation event = resolveEvent(identifier);
        final Minecraft minecraft = Minecraft.getInstance();
        if (event == null || !isRegistered(minecraft, event)) return false;
        minecraft.getSoundManager().play(new SimpleSoundInstance(
                event, source, volume, pitch, RandomSource.create(), false, 0,
                SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
        return true;
    }

    static ResourceLocation resolveEvent(String identifier) {
        if (identifier == null || identifier.isBlank()) return null;
        final String value = identifier.trim();
        try {
            // Explicit Java identifiers remain valid for compatibility. Bare Bedrock identifiers
            // use the stable aliases emitted by ViaBedrock's resource-pack rewriter.
            return value.indexOf(':') >= 0
                    ? ResourceLocation.parse(value)
                    : ResourceLocation.fromNamespaceAndPath("bedrock", value);
        } catch (RuntimeException exception) {
            ViaBedrockUtilityNeoForge.LOGGER.warn("[Sound] Invalid sound identifier '{}'", identifier);
            return null;
        }
    }

    private static boolean isRegistered(Minecraft minecraft, ResourceLocation event) {
        if (minecraft.getSoundManager().getSoundEvent(event) != null) return true;
        if (WARNED_MISSING.add(event)) {
            ViaBedrockUtilityNeoForge.LOGGER.warn(
                    "[Sound] Java sound event '{}' is not registered by the active resource pack", event);
        }
        return false;
    }

    public static void clearDiagnostics() {
        WARNED_MISSING.clear();
    }
}
