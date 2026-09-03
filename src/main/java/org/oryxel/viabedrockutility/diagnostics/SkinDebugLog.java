package org.oryxel.viabedrockutility.diagnostics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded diagnostics for skin payload assembly and cached player-renderer replacement. */
public final class SkinDebugLog {
    private static final int MAX_EVENTS = 128;
    private final AtomicLong sequence = new AtomicLong();
    private final Map<UUID, MutableTransfer> active = new LinkedHashMap<>();
    private final Map<UUID, InstalledSkin> installed = new LinkedHashMap<>();
    private final Deque<Event> events = new ArrayDeque<>();

    public synchronized long begin(UUID playerUuid, long clientTick, int width, int height,
                                   int expectedChunks, String geometryIdentifier,
                                   String geometryRaw, String resourcePatch) {
        final long transfer = sequence.incrementAndGet();
        final MutableTransfer previous = active.put(playerUuid,
                new MutableTransfer(transfer, clientTick, width, height,
                expectedChunks, geometryIdentifier,
                BedrockPackDiagnostics.shortHash(BedrockPackDiagnostics.hashText(geometryRaw)),
                BedrockPackDiagnostics.shortHash(BedrockPackDiagnostics.hashText(resourcePatch))));
        if (previous != null) {
            add(new Event(previous.sequence, playerUuid, clientTick, Kind.SUPERSEDED,
                    "newSequence=" + transfer + ",received=" + previous.received.cardinality()
                            + "/" + previous.expectedChunks + ",wireTransferId=absent"));
        }
        add(new Event(transfer, playerUuid, clientTick, Kind.INFORMATION,
                "size=" + width + "x" + height + ",chunks=" + expectedChunks
                        + ",geometry=" + geometryIdentifier));
        return transfer;
    }

    public synchronized void chunk(UUID playerUuid, long transfer, long clientTick,
                                   int index, int bytes) {
        final MutableTransfer state = active.get(playerUuid);
        if (state == null || state.sequence != transfer) {
            add(new Event(transfer, playerUuid, clientTick, Kind.ORPHAN_CHUNK,
                    "index=" + index + ",bytes=" + bytes));
            return;
        }
        if (index >= 0 && index < state.expectedChunks) {
            state.received.set(index);
        }
        add(new Event(transfer, playerUuid, clientTick, Kind.CHUNK,
                "index=" + index + ",bytes=" + bytes + ",received="
                        + state.received.cardinality() + "/" + state.expectedChunks));
    }

    public synchronized void installed(UUID playerUuid, long transfer, long clientTick,
                                       String geometryIdentifier, boolean slim,
                                       Object renderer, Object model, String textureIdentifier) {
        final MutableTransfer state = active.remove(playerUuid);
        final InstalledSkin value = new InstalledSkin(transfer, clientTick, geometryIdentifier, slim,
                System.identityHashCode(renderer), System.identityHashCode(model),
                Objects.requireNonNullElse(textureIdentifier, ""),
                state == null ? "" : state.geometryHash,
                state == null ? "" : state.resourcePatchHash);
        installed.put(playerUuid, value);
        add(new Event(transfer, playerUuid, clientTick, Kind.INSTALLED,
                "geometry=" + geometryIdentifier + ",renderer=" + value.rendererIdentity
                        + ",model=" + value.modelIdentity));
    }

    public synchronized void rejected(UUID playerUuid, long transfer, long clientTick, String detail) {
        final MutableTransfer state = active.get(playerUuid);
        if (state != null && state.sequence == transfer) {
            active.remove(playerUuid);
        }
        add(new Event(transfer, playerUuid, clientTick, Kind.REJECTED,
                Objects.requireNonNullElse(detail, "")));
    }

    public synchronized void removed(UUID playerUuid, long clientTick, String reason) {
        final MutableTransfer state = active.remove(playerUuid);
        final InstalledSkin previous = installed.remove(playerUuid);
        final long transfer = state != null ? state.sequence : previous != null ? previous.sequence : -1L;
        add(new Event(transfer, playerUuid, clientTick, Kind.REMOVED,
                Objects.requireNonNullElse(reason, "")));
    }

    public synchronized Snapshot snapshot(UUID playerUuid) {
        final MutableTransfer transfer = active.get(playerUuid);
        final TransferSnapshot activeSnapshot = transfer == null ? null : transfer.snapshot();
        final List<Event> matching = events.stream()
                .filter(event -> playerUuid == null || playerUuid.equals(event.playerUuid))
                .toList();
        return new Snapshot(playerUuid, activeSnapshot,
                playerUuid == null ? null : installed.get(playerUuid), matching);
    }

    public synchronized void clear() {
        active.clear();
        installed.clear();
        events.clear();
    }

    private void add(Event event) {
        events.addLast(event);
        while (events.size() > MAX_EVENTS) {
            events.removeFirst();
        }
    }

    public enum Kind {
        INFORMATION,
        CHUNK,
        SUPERSEDED,
        ORPHAN_CHUNK,
        INSTALLED,
        REJECTED,
        REMOVED
    }

    public record Event(long sequence, UUID playerUuid, long clientTick, Kind kind, String detail) {
    }

    public record InstalledSkin(long sequence, long installedTick, String geometryIdentifier,
                                boolean slim, int rendererIdentity, int modelIdentity,
                                String textureIdentifier, String geometryHash,
                                String resourcePatchHash) {
    }

    public record TransferSnapshot(long sequence, long startedTick, int width, int height,
                                   int expectedChunks, List<Integer> receivedChunks,
                                   String geometryIdentifier, String geometryHash,
                                   String resourcePatchHash) {
        public TransferSnapshot {
            receivedChunks = List.copyOf(receivedChunks);
        }
    }

    public record Snapshot(UUID playerUuid, TransferSnapshot active,
                           InstalledSkin installed, List<Event> events) {
        public Snapshot {
            events = List.copyOf(events);
        }
    }

    private static final class MutableTransfer {
        private final long sequence;
        private final long startedTick;
        private final int width;
        private final int height;
        private final int expectedChunks;
        private final String geometryIdentifier;
        private final String geometryHash;
        private final String resourcePatchHash;
        private final BitSet received = new BitSet();

        private MutableTransfer(long sequence, long startedTick, int width, int height,
                                int expectedChunks, String geometryIdentifier,
                                String geometryHash, String resourcePatchHash) {
            this.sequence = sequence;
            this.startedTick = startedTick;
            this.width = width;
            this.height = height;
            this.expectedChunks = Math.max(0, expectedChunks);
            this.geometryIdentifier = Objects.requireNonNullElse(geometryIdentifier, "");
            this.geometryHash = geometryHash;
            this.resourcePatchHash = resourcePatchHash;
        }

        private TransferSnapshot snapshot() {
            final List<Integer> chunks = new ArrayList<>();
            for (int index = received.nextSetBit(0); index >= 0; index = received.nextSetBit(index + 1)) {
                chunks.add(index);
            }
            return new TransferSnapshot(sequence, startedTick, width, height, expectedChunks,
                    chunks, geometryIdentifier, geometryHash, resourcePatchHash);
        }
    }
}
