package org.oryxel.viabedrockutility.pack.api;

import org.oryxel.viabedrockutility.ViaBedrockUtility;
import java.util.List;

/** Stable cross-mod read boundary. Does not expose PackManager, Content or renderer internals. */
public final class BedrockPackResources {
    @FunctionalInterface
    public interface Pack {
        /** A private byte copy, or null for a missing entry. Enforces the limit before copying. */
        byte[] read(String path, int maximumBytes);
    }

    public record Snapshot(long connection, long generation, List<Pack> packs) {
        public Snapshot { packs = List.copyOf(packs); }
    }

    private static Snapshot cached;

    private BedrockPackResources() {}

    /** Returns one cached immutable view per published generation; it does not imply GPU readiness. */
    public static Snapshot snapshot() {
        var utility = ViaBedrockUtility.getInstance();
        synchronized (utility) {
            var generation = utility.getPackGeneration();
            long connection = utility.getConnectionEpoch();
            if (cached == null || cached.connection() != connection || cached.generation() != generation.generation()) {
                List<Pack> packs = generation.manager() == null ? List.of()
                        : generation.manager().getPacks().stream().<Pack>map(pack -> (path, maximumBytes) -> {
                            if (maximumBytes < 1) throw new IllegalArgumentException("Invalid resource byte limit");
                            byte[] value = pack.get(path);
                            if (value == null) return null;
                            if (value.length > maximumBytes) throw new IllegalArgumentException("Resource exceeds byte limit: " + path);
                            return value.clone();
                        }).toList();
                cached = new Snapshot(connection, generation.generation(), packs);
            }
            return cached;
        }
    }
}
