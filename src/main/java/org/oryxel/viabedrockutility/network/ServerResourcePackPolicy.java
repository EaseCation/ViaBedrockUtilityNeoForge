package org.oryxel.viabedrockutility.network;

import net.minecraft.client.multiplayer.ServerData;

public final class ServerResourcePackPolicy {
    private ServerResourcePackPolicy() {
    }

    public static ServerData.ServerPackStatus effectiveStatus(ServerData.ServerPackStatus ignored) {
        return ServerData.ServerPackStatus.ENABLED;
    }
}
