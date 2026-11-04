package org.oryxel.viabedrockutility.attachable;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.neoforge.client.model.pipeline.VertexConsumerWrapper;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.item.BedrockItemIdentity;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Owned affine texture coordinates for a held attachable. No action clocks or layer arbitration.
 * Coordinates remain unwrapped through interpolation; the pack texture's GPU sampler repeats.
 */
public final class AttachableSurfaces {
    public record Uv(float offsetU, float offsetV, float scaleU, float scaleV) {
        public static final Uv IDENTITY = new Uv(0, 0, 1, 1);
        public Uv {
            if (!Float.isFinite(offsetU) || !Float.isFinite(offsetV)
                    || !Float.isFinite(scaleU) || !Float.isFinite(scaleV) || scaleU <= 0 || scaleV <= 0)
                throw new IllegalArgumentException("Surface UV must be finite with positive scale");
        }
    }
    private record Key(UUID actor, AttachableQueryContext.LogicalHand hand) {}
    private static final Map<Key, Lease> ACTIVE = new HashMap<>();
    private AttachableSurfaces() {}

    public static Lease acquire(AbstractClientPlayer player, String item, boolean offhand) {
        requireThread(); Objects.requireNonNull(player); Objects.requireNonNull(item);
        var hand = offhand ? AttachableQueryContext.LogicalHand.OFF_HAND : AttachableQueryContext.LogicalHand.MAIN_HAND;
        var key = new Key(player.getUUID(), hand);
        var previous = ACTIVE.get(key);
        if (previous != null && previous.isCurrent()) throw new IllegalStateException("Attachable surface already owned");
        if (previous != null) previous.close();
        var next = new Lease(key, player, item);
        if (!next.isCurrent()) throw new IllegalStateException("Surface requires the actual held item");
        ACTIVE.put(key, next);
        return next;
    }

    public static final class Lease implements AutoCloseable {
        private final Key key;
        private final AbstractClientPlayer player;
        private final String item;
        private final net.minecraft.resources.ResourceLocation itemKey;
        private final long connection, generation;
        private Uv uv = Uv.IDENTITY;
        private boolean released;
        private Lease(Key key, AbstractClientPlayer player, String item) {
            this.key = key; this.player = player; this.item = item;
            this.itemKey = net.minecraft.resources.ResourceLocation.parse(item);
            var host = ViaBedrockUtility.getInstance();
            connection = host.getConnectionEpoch(); generation = host.getPackGeneration().generation();
        }
        public boolean isCurrent() {
            requireThread();
            var level = Minecraft.getInstance().level;
            if (released || level == null || player.level() != level || player.isRemoved()
                    || level.getEntity(player.getId()) != player) return false;
            var host = ViaBedrockUtility.getInstance();
            if (host.getConnectionEpoch() != connection || host.getPackGeneration().generation() != generation) return false;
            var held = BedrockItemIdentity.resolve(key.hand == AttachableQueryContext.LogicalHand.MAIN_HAND
                    ? player.getMainHandItem() : player.getOffhandItem());
            return itemKey.equals(held);
        }
        public void publish(Uv value) {
            requireThread(); if (!isCurrent()) throw new IllegalStateException("Surface lease retired");
            uv = Objects.requireNonNull(value);
        }
        @Override public void close() { requireThread(); released = true; ACTIVE.remove(key, this); uv = Uv.IDENTITY; }
    }

    static void tick() {
        ACTIVE.values().removeIf(lease -> {
            if (lease.isCurrent()) return false;
            lease.released = true; lease.uv = Uv.IDENTITY; return true;
        });
    }

    static Uv sample(UUID actor, String item, AttachableQueryContext.LogicalHand hand) {
        var lease = ACTIVE.get(new Key(actor, hand));
        return lease != null && lease.item.equals(item) && lease.isCurrent() ? lease.uv : Uv.IDENTITY;
    }

    static VertexConsumer transform(VertexConsumer parent, Uv uv) {
        if (uv.equals(Uv.IDENTITY)) return parent;
        return new VertexConsumerWrapper(parent) {
            @Override public VertexConsumer setUv(float u, float v) {
                parent.setUv(uv.offsetU + u * uv.scaleU, uv.offsetV + v * uv.scaleV); return this;
            }
            @Override public void addVertex(float x, float y, float z, int color, float u, float v,
                                            int overlay, int light, float nx, float ny, float nz) {
                parent.addVertex(x, y, z, color, uv.offsetU + u * uv.scaleU, uv.offsetV + v * uv.scaleV,
                        overlay, light, nx, ny, nz);
            }
        };
    }
    private static void requireThread() {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Surface mutations require client thread");
    }
}
