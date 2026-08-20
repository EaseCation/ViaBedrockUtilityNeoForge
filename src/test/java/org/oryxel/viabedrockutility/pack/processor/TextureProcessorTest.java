package org.oryxel.viabedrockutility.pack.processor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextureProcessorTest {
    @Test
    void usesTheSameCanonicalPathForBedrockParticleTextures() {
        assertEquals("minecraft:textures/particle/bullet_laser",
                TextureProcessor.normalizeTextureIdentifier(
                        "textures/particle/Bullet_Laser.PNG").toString());
        assertEquals("easecation:textures/particle/trail",
                TextureProcessor.normalizeTextureIdentifier(
                        "easecation:textures/particle/trail.jpg").toString());
    }
}
