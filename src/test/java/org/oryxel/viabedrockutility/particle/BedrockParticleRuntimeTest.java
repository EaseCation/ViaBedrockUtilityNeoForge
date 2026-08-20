package org.oryxel.viabedrockutility.particle;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BedrockParticleRuntimeTest {
    @Test
    void normalizesBedrockIdentifiersWithoutChangingTheirPath() {
        assertEquals("easecation:bullet_laser",
                BedrockParticleRuntime.normalizeIdentifier(" EaseCation:Bullet_Laser "));
        assertEquals("minecraft:ambient",
                BedrockParticleRuntime.normalizeIdentifier("ambient"));
        assertEquals("easecation:trails/laser",
                BedrockParticleRuntime.normalizeIdentifier("easecation:trails/laser"));
    }

    @Test
    void rejectsBlankAndMalformedIdentifiers() {
        assertNull(BedrockParticleRuntime.normalizeIdentifier(null));
        assertNull(BedrockParticleRuntime.normalizeIdentifier("  "));
        assertNull(BedrockParticleRuntime.normalizeIdentifier("easecation:bad id"));
    }

    @Test
    void spawnParticleEffectEntityCoordinatesRemainLocalOffsets() {
        final UUID owner = UUID.randomUUID();
        final BedrockParticleRequest request = BedrockParticleRuntime.spawnParticleEffectEntityRequest(
                "test:bound", owner, 1.25F, -2.5F, 3.75F, Map.of("phase", 4.0F));
        final BedrockParticlePlacement.BoundEffect placement =
                (BedrockParticlePlacement.BoundEffect) request.placement();

        assertEquals(owner, placement.ownerUuid());
        assertEquals(BedrockParticlePlacement.TargetKind.ENTITY, placement.targetKind());
        assertEquals(BedrockParticlePlacement.OrientationPolicy.OWNER_ROOT,
                placement.orientationPolicy());
        assertEquals(1.25F, placement.localOffset().x, 0.0F);
        assertEquals(-2.5F, placement.localOffset().y, 0.0F);
        assertEquals(3.75F, placement.localOffset().z, 0.0F);
        assertEquals("viabedrock-v2-entity", request.source());
        assertEquals(4.0F, request.variables().get("phase"), 0.0F);
    }
}
