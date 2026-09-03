package org.oryxel.viabedrockutility.diagnostics;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinDebugLogTest {
    @Test
    void tracksTransferChunksAndInstalledRendererIdentity() {
        final SkinDebugLog log = new SkinDebugLog();
        final UUID player = UUID.randomUUID();
        final Object renderer = new Object();
        final Object model = new Object();
        final long transfer = log.begin(player, 10L, 128, 128, 2,
                "geometry.example", "geometry-json", "patch-json");

        log.chunk(player, transfer, 11L, 0, 1024);
        log.chunk(player, transfer, 12L, 1, 512);
        log.installed(player, transfer, 13L, "geometry.example", false,
                renderer, model, "vbu:skin");

        final SkinDebugLog.Snapshot snapshot = log.snapshot(player);
        assertNull(snapshot.active());
        assertEquals(transfer, snapshot.installed().sequence());
        assertEquals(System.identityHashCode(renderer), snapshot.installed().rendererIdentity());
        assertEquals(System.identityHashCode(model), snapshot.installed().modelIdentity());
        assertEquals(4, snapshot.events().size());
        assertEquals(SkinDebugLog.Kind.INSTALLED, snapshot.events().getLast().kind());
    }

    @Test
    void recordsWhenNewInformationSupersedesAnIncompleteTransfer() {
        final SkinDebugLog log = new SkinDebugLog();
        final UUID player = UUID.randomUUID();
        final long first = log.begin(player, 1L, 64, 64, 1,
                "geometry.first", "first", "first");
        final long second = log.begin(player, 2L, 64, 64, 1,
                "geometry.second", "second", "second");

        final SkinDebugLog.Snapshot snapshot = log.snapshot(player);
        assertEquals(second, snapshot.active().sequence());
        assertTrue(snapshot.events().stream()
                .anyMatch(event -> event.kind() == SkinDebugLog.Kind.SUPERSEDED
                        && event.sequence() == first));
    }
}
