package org.oryxel.viabedrockutility.attachable;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.animation.BedrockFrameTime;
import org.oryxel.viabedrockutility.item.BedrockItemIdentity;
import java.util.*;

/** Opt-in, selected attachable anchors from actual third-person drawing. No animation evaluation.
 * Returned matrices use native world block coordinates and Java model axes. FP viewmodels have
 * a different projection contract and deliberately never publish through this world port.
 */
public final class AttachableWorldPoses {
    public record Target(String bone, String locator) {
        public Target { name(bone); if (locator == null || locator.length() > 256) throw new IllegalArgumentException("Invalid locator"); }
    }
    /** Installation data, in the exact model origin used by GeometryUtil. Empty locator means pivot. */
    public record Local(Matrix4f transform, boolean ignoreInheritedScale) {
        public Local { transform = new Matrix4f(transform); }
        @Override public Matrix4f transform() { return new Matrix4f(transform); }
    }
    public record Snapshot(long frame, long connection, long packGeneration, Matrix4f world) {
        public Snapshot { world = new Matrix4f(world); }
        @Override public Matrix4f world() { return new Matrix4f(world); }
    }
    private record Key(UUID owner, AttachableQueryContext.LogicalHand hand) {}
    private static final Map<Key, List<Lease>> ACTIVE = new HashMap<>();
    private static Drawing drawing;
    private static World world;
    private static int count;
    public interface Scope extends AutoCloseable { default void commit() {} @Override void close(); }
    private static final Scope UNCHANGED = () -> {};
    private AttachableWorldPoses() {}

    /** Actual LevelRenderer entity boundary; inventory/paperdoll drawing is not world space. */
    public static Scope world(Entity entity, double cameraX, double cameraY, double cameraZ) {
        if (ACTIVE.isEmpty()) return UNCHANGED;
        World next = new World(world, entity.getUUID(), cameraX, cameraY, cameraZ);
        world = next;
        return next;
    }
    private record World(World previous, UUID owner, double x, double y, double z) implements Scope {
        @Override public void close() {
            if (world != this) throw new IllegalStateException("Unbalanced world anchor scope");
            world = previous;
        }
    }

    public static Lease acquire(AbstractClientPlayer player, String itemIdentifier, boolean offhand, Map<String, Target> targets) {
        thread(); name(itemIdentifier);
        if (targets.isEmpty() || targets.size() > 64 || count >= 128) throw new IllegalArgumentException("Anchor subscription limit");
        targets.forEach((alias, target) -> { name(alias); Objects.requireNonNull(target); });
        var hand = offhand ? AttachableQueryContext.LogicalHand.OFF_HAND : AttachableQueryContext.LogicalHand.MAIN_HAND;
        Lease lease = new Lease(Objects.requireNonNull(player), itemIdentifier, hand, targets);
        if (!lease.isCurrent()) throw new IllegalStateException("Anchor subscription requires a present holder and actual item");
        ACTIVE.computeIfAbsent(lease.key, ignored -> new ArrayList<>()).add(lease); count++;
        return lease;
    }

    public static final class Lease implements AutoCloseable {
        private final AbstractClientPlayer player;
        private final String item;
        private final Key key;
        private final long connection, generation;
        private final Map<String, Target> targets;
        private final Map<String, List<Map.Entry<String, Target>>> byBone = new HashMap<>();
        private final Map<String, Snapshot> samples = new HashMap<>();
        private final Set<String> conflicts = new HashSet<>();
        private long frame = -1;
        private boolean released;
        private Lease(AbstractClientPlayer player, String item, AttachableQueryContext.LogicalHand hand, Map<String, Target> targets) {
            this.player = player; this.item = item; this.key = new Key(player.getUUID(), hand); this.targets = Map.copyOf(targets);
            this.targets.entrySet().forEach(entry -> byBone.computeIfAbsent(entry.getValue().bone, ignored -> new ArrayList<>()).add(entry));
            var host = ViaBedrockUtility.getInstance(); connection = host.getConnectionEpoch(); generation = host.getPackGeneration().generation();
        }
        public boolean isCurrent() {
            var client = Minecraft.getInstance(); var host = ViaBedrockUtility.getInstance();
            if (released || client.level == null || client.level != player.level() || player.isRemoved()
                    || client.level.getPlayerByUUID(key.owner) != player || connection != host.getConnectionEpoch()
                    || generation != host.getPackGeneration().generation()) return false;
            var held = BedrockItemIdentity.resolve(key.hand == AttachableQueryContext.LogicalHand.MAIN_HAND ? player.getMainHandItem() : player.getOffhandItem());
            return held != null && item.equals(held.toString());
        }
        /** Same display frame only. Not rendered, culled, hidden and conflicting passes are unavailable. */
        public Optional<Snapshot> sample(String alias) {
            thread();
            if (!targets.containsKey(alias)) throw new IllegalArgumentException("Undeclared anchor: " + alias);
            if (!isCurrent() || frame != BedrockFrameTime.INSTANCE.frame() || conflicts.contains(alias)) return Optional.empty();
            return Optional.ofNullable(samples.get(alias));
        }
        private void accept(String alias, Matrix4f matrix) {
            long current = BedrockFrameTime.INSTANCE.frame();
            if (frame != current) { frame = current; samples.clear(); conflicts.clear(); }
            Snapshot previous = samples.get(alias);
            if (previous != null && !previous.world.equals(matrix, 1e-5f)) { conflicts.add(alias); samples.remove(alias); return; }
            if (!conflicts.contains(alias) && previous == null) samples.put(alias, new Snapshot(frame, connection, generation, matrix));
        }
        @Override public void close() {
            thread(); if (released) return; released = true; samples.clear(); conflicts.clear();
            var leases = ACTIVE.get(key);
            if (leases != null && leases.remove(this)) { count--; if (leases.isEmpty()) ACTIVE.remove(key); }
        }
    }

    /** Render-thread scope installed only around an actual attachable material submission. */
    static Scope geometry(UUID owner, String item, AttachableQueryContext.LogicalHand hand,
                          AttachableQueryContext.ViewContext view, Map<Target, Local> locals) {
        // A nested FP/non-owned draw must mask any outer capture scope.
        var leases = view == AttachableQueryContext.ViewContext.THIRD_PERSON && world != null && world.owner.equals(owner)
                ? ACTIVE.get(new Key(owner, hand)) : null;
        if (leases == null && drawing == null) return UNCHANGED;
        Drawing next = new Drawing(drawing, leases, item, locals);
        drawing = next;
        return next;
    }
    private static final class Drawing implements Scope {
        final Drawing previous;
        final List<Lease> leases;
        final String item;
        final Map<Target, Local> locals;
        final World placement;
        final Map<Lease, Map<String, Matrix4f>> pending = new IdentityHashMap<>();
        boolean committed;
        Drawing(Drawing previous, List<Lease> leases, String item, Map<Target, Local> locals) {
            this.previous = previous; this.leases = leases; this.item = item; this.locals = locals;
            this.placement = world;
        }
        @Override public void close() {
            if (drawing != this) throw new IllegalStateException("Unbalanced attachable anchor scope");
            drawing = previous;
            if (committed) pending.forEach((lease, samples) -> samples.forEach(lease::accept));
        }
        @Override public void commit() { committed = true; }
    }

    /** Called after the native bone's actual transform, including relative_to and host inheritance. */
    public static void recordBone(String bone, boolean cubeGroup, PoseStack poses) {
        Drawing context = drawing;
        if (context == null || context.leases == null || cubeGroup) return;
        for (Lease lease : context.leases) {
            var requested = lease.byBone.get(bone);
            if (requested == null) continue;
            if (!lease.item.equals(context.item) || !lease.isCurrent()) continue;
            for (var entry : requested) {
                Local local = context.locals.get(entry.getValue());
                if (local == null) continue;
                Matrix4f base = new Matrix4f(poses.last().pose());
                float determinant = base.determinant3x3();
                // A zero-scale hidden ancestor has no usable oriented anchor; never revive it.
                if (!Float.isFinite(determinant) || Math.abs(determinant) < 1e-12f) continue;
                Matrix4f anchor = new Matrix4f(base).mul(local.transform);
                if (local.ignoreInheritedScale) {
                    Vector3f position = anchor.getTranslation(new Vector3f());
                    base.normalize3x3();
                    anchor.set(base).mul(local.transform).setTranslation(position);
                }
                var camera = context.placement;
                anchor.m30(anchor.m30() + (float)camera.x).m31(anchor.m31() + (float)camera.y).m32(anchor.m32() + (float)camera.z);
                if (anchor.isFinite()) context.pending.computeIfAbsent(lease, ignored -> new LinkedHashMap<>()).put(entry.getKey(), anchor);
            }
        }
    }
    static void tick() {
        if (ACTIVE.isEmpty()) return;
        List<Lease> retired = new ArrayList<>();
        ACTIVE.values().forEach(leases -> leases.forEach(lease -> { if (!lease.isCurrent()) retired.add(lease); }));
        retired.forEach(Lease::close);
    }
    static void clear() {
        for (var leases : ACTIVE.values()) for (Lease lease : leases) { lease.released = true; lease.samples.clear(); lease.conflicts.clear(); }
        ACTIVE.clear(); count = 0;
    }
    private static void thread() { if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Client thread required"); }
    private static void name(String value) { if (value == null || value.isBlank() || value.length() > 256) throw new IllegalArgumentException("Invalid anchor identifier"); }
}
