package org.oryxel.viabedrockutility.network;

import net.minecraft.client.multiplayer.ServerData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerResourcePackPolicyTest {
    @Test
    void everySavedStatusIsTreatedAsEnabled() {
        for (ServerData.ServerPackStatus status : ServerData.ServerPackStatus.values()) {
            assertEquals(
                    ServerData.ServerPackStatus.ENABLED,
                    ServerResourcePackPolicy.effectiveStatus(status),
                    () -> "Expected automatic acceptance for " + status
            );
        }
    }

    @Test
    void clientListenerLoadsWithRequiredMixin() {
        assertDoesNotThrow(() -> Class.forName(
                "net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl"
        ));
    }
}
