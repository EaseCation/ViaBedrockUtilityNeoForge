package org.oryxel.viabedrockutility.network;

import net.minecraft.client.multiplayer.ServerData;

public final class ServerResourcePackPolicy {
    private ServerResourcePackPolicy() {
    }

    public static ServerData.ServerPackStatus autoAccept(
            ServerData.ServerPackStatus ignored,
            Runnable allowServerPacks
    ) {
        allowServerPacks.run();
        return ServerData.ServerPackStatus.ENABLED;
    }
}
