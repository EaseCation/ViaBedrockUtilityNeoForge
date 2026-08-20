package org.oryxel.viabedrockutility.particle;

/** Resolves one host anchor without exposing mutable Minecraft model parts to BEParticle. */
@FunctionalInterface
public interface BedrockPoseProvider {
    BedrockPoseSnapshot resolve(BedrockParticlePlacement.BoundEffect placement);

    default void clear() {
    }
}
