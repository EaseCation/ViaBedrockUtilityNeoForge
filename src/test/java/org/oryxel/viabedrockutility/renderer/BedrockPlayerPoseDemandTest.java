package org.oryxel.viabedrockutility.renderer;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockPlayerPoseDemandTest {
    @Test
    void demandCoversTheRequestTickAndOneFollowingCaptureTick() {
        final BedrockPlayerPoseDemand demand = new BedrockPlayerPoseDemand();
        final UUID owner = UUID.randomUUID();

        assertFalse(demand.isRequested(owner, 40L));

        demand.request(owner, 40L);
        assertTrue(demand.isRequested(owner, 40L));
        assertTrue(demand.isRequested(owner, 41L));
        assertFalse(demand.isRequested(owner, 42L));

        demand.prune(42L);
        assertFalse(demand.isRequested(owner, 42L));

        demand.request(owner, 43L);
        demand.clear();
        assertFalse(demand.isRequested(owner, 43L));
    }
}
