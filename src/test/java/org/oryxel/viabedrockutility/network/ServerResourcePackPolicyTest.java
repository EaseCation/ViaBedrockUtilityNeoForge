package org.oryxel.viabedrockutility.network;

import net.minecraft.client.multiplayer.ServerData;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerResourcePackPolicyTest {
    @Test
    void everySavedStatusAllowsPacksAndIsTreatedAsEnabled() {
        for (ServerData.ServerPackStatus status : ServerData.ServerPackStatus.values()) {
            final AtomicInteger allowCalls = new AtomicInteger();

            assertEquals(
                    ServerData.ServerPackStatus.ENABLED,
                    ServerResourcePackPolicy.autoAccept(status, allowCalls::incrementAndGet),
                    () -> "Expected automatic acceptance for " + status
            );
            assertEquals(1, allowCalls.get(), () -> "Expected one allow call for " + status);
        }
    }

    @Test
    void clientListenerLoadsWithRequiredMixin() {
        assertDoesNotThrow(() -> Class.forName(
                "net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl"
        ));
    }
}
