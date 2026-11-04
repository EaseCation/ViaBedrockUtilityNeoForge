package org.oryxel.viabedrockutility.attachable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.oryxel.viabedrockutility.item.BedrockItemIdentity;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.oryxel.viabedrockutility.ViaBedrockUtility;

/** Opt-in camera-space ownership for authored viewmodels. Never changes the world camera or options. */
public final class FirstPersonViewSpace {
    private static final EnumMap<AttachableQueryContext.LogicalHand, Lease> ACTIVE =
            new EnumMap<>(AttachableQueryContext.LogicalHand.class);
    private static final ThreadLocal<Lease> DRAWING = new ThreadLocal<>();
    private static final ThreadLocal<String> ROOT = new ThreadLocal<>();
    public interface Scope extends AutoCloseable { @Override void close(); }
    private static final Scope UNCHANGED = () -> {};
    private static final ThreadLocal<RootStack> ROOT_STACK = ThreadLocal.withInitial(RootStack::new);
    private static final class RootStack {
        final RootFrame[] frames = new RootFrame[16];
        int depth;
    }
    private static final class RootFrame implements Scope {
        final RootStack stack;
        final int index;
        String previous;
        RootFrame(RootStack stack, int index) { this.stack = stack; this.index = index; }
        @Override public void close() {
            if (stack.depth != index + 1) throw new IllegalStateException("Unbalanced camera-space geometry");
            stack.depth--;
            if (previous == null) ROOT.remove(); else ROOT.set(previous);
            previous = null;
        }
    }
    private FirstPersonViewSpace() {}

    public static Lease acquire(AbstractClientPlayer player, String itemIdentifier, boolean offhand,
                                Map<String, String> geometryRoots) {
        requireThread();
        if (player != Minecraft.getInstance().player) throw new IllegalArgumentException("View space belongs to the local player");
        if (geometryRoots.isEmpty() || geometryRoots.size() > 32) throw new IllegalArgumentException("Declare bounded camera-space geometries");
        var models = ViaBedrockUtility.getInstance().getPackManager().getModelDefinitions().getEntityModels();
        geometryRoots.forEach((geometry, root) -> {
            var model = models.get(geometry);
            if (root == null || root.isBlank() || model == null
                    || model.getParents().stream().noneMatch(bone -> root.equals(bone.getName()))) {
                throw new IllegalArgumentException("Missing camera-space root: " + geometry + "/" + root);
            }
        });
        var hand = offhand ? AttachableQueryContext.LogicalHand.OFF_HAND : AttachableQueryContext.LogicalHand.MAIN_HAND;
        Lease previous = ACTIVE.get(hand);
        if (previous != null && previous.isCurrent()) throw new IllegalStateException("First-person view space already owned");
        if (previous != null) previous.close();
        Lease lease = new Lease(player, itemIdentifier, hand, geometryRoots);
        if (!lease.isCurrent()) throw new IllegalStateException("View space requires the actual held item");
        ACTIVE.put(hand, lease);
        return lease;
    }

    public static final class Lease implements AutoCloseable {
        private final AbstractClientPlayer player;
        private final String item;
        private final AttachableQueryContext.LogicalHand hand;
        private final Matrix4f cameraToSubmission = new Matrix4f();
        private final Matrix4f cameraRoot = new Matrix4f();
        private final Map<String, String> geometryRoots;
        private final long connection, generation;
        private boolean prepared;
        private boolean requiresOwnedGeometry;
        private boolean released;
        private Lease(AbstractClientPlayer player, String item, AttachableQueryContext.LogicalHand hand,
                      Map<String, String> geometryRoots) {
            this.player = player; this.item = item; this.hand = hand;
            this.geometryRoots = Map.copyOf(geometryRoots);
            var host = ViaBedrockUtility.getInstance();
            this.connection = host.getConnectionEpoch(); this.generation = host.getPackGeneration().generation();
        }
        public boolean isCurrent() {
            var client = Minecraft.getInstance();
            if (released || client.player != player || client.level != player.level() || player.isRemoved()) return false;
            var host = ViaBedrockUtility.getInstance();
            if (host.getConnectionEpoch() != connection || host.getPackGeneration().generation() != generation) return false;
            var held = BedrockItemIdentity.resolve(hand == AttachableQueryContext.LogicalHand.MAIN_HAND
                    ? player.getMainHandItem() : player.getOffhandItem());
            return held != null && item.equals(held.toString());
        }

        /** Camera-local, block-unit root pose. Author sampling below this root remains native.
         * The source arrays are copied into the owned matrix; no caller-owned mutable state is kept.
         */
        public void setCameraSpace(double[] rotation, double[] position, double[] scale) {
            requireThread();
            if (!isCurrent()) throw new IllegalStateException("View space is retired");
            check(rotation, 9); check(position, 3); check(scale, 3);
            for (double value : scale) if (value <= 0) throw new IllegalArgumentException("Reference space scale must be positive");
            Matrix4f candidate = new Matrix4f().translation((float)position[0], (float)position[1], (float)position[2]);
            candidate.m00((float)rotation[0]).m10((float)rotation[1]).m20((float)rotation[2]);
            candidate.m01((float)rotation[3]).m11((float)rotation[4]).m21((float)rotation[5]);
            candidate.m02((float)rotation[6]).m12((float)rotation[7]).m22((float)rotation[8]);
            candidate.scale((float)scale[0], (float)scale[1], (float)scale[2]);
            if (!Float.isFinite(candidate.determinant()) || Math.abs(candidate.determinant()) < 1.0e-12F) {
                throw new IllegalArgumentException("Degenerate camera reference space");
            }
            cameraRoot.set(candidate);
            prepared = true;
        }
        @Override public void close() {
            requireThread();
            released = true;
            ACTIVE.remove(hand, this);
        }
    }

    static void tick() {
        ACTIVE.values().removeIf(lease -> {
            if (lease.isCurrent()) return false;
            lease.released = true; return true;
        });
    }

    /** Vanilla's hand event can still carry the outgoing (or empty) stack for several ticks.
     * An installed camera-space owner has its own equip presentation; select its actual held
     * stack only after its reference pose is prepared. Never mutate the vanilla hand cache.
     */
    static ItemStack renderItem(AbstractClientPlayer player, AttachableQueryContext.LogicalHand hand,
                                ItemStack nativeItem) {
        Lease lease = ACTIVE.get(hand);
        if (lease == null || lease.player != player || !lease.prepared || !lease.isCurrent()) return nativeItem;
        return hand == AttachableQueryContext.LogicalHand.MAIN_HAND
                ? player.getMainHandItem() : player.getOffhandItem();
    }

    /** Runs within the event's pushed stack. Pop restores all other render consumers. */
    static boolean canRenderWithoutPlayerPose(UUID owner, AttachableItemSnapshot item, AttachableQueryContext.LogicalHand hand) {
        Lease lease = ACTIVE.get(hand);
        return lease != null && lease.prepared && lease.isCurrent() && lease.player.getUUID().equals(owner)
                && item != null && !item.isEmpty() && lease.item.equals(item.itemIdentifier().toString());
    }

    static boolean apply(UUID owner, AttachableItemSnapshot item, AttachableQueryContext.LogicalHand hand,
                         PoseStack poses, boolean playerPoseAvailable) {
        DRAWING.remove(); ROOT.remove();
        if (item == null || item.isEmpty()) return true;
        Lease lease = ACTIVE.get(hand);
        if (lease == null || !lease.player.getUUID().equals(owner) || !lease.item.equals(item.itemIdentifier().toString())) return true;
        if (!lease.isCurrent()) { lease.close(); return true; }
        var global = RenderSystem.getModelViewMatrix();
        float determinant = global.determinant();
        if (!Float.isFinite(determinant) || Math.abs(determinant) < 1.0e-8F) {
            AttachableDebugLog.warnOnce("owned-view-space", "[Attachable] Camera submission basis is unavailable", null);
            return false;
        }
        // ItemInHandRenderer adds lagged pitch/yaw and bobbing before RenderHandEvent. The caller
        // supplies a complete authored camera-local pose, so preserve the final camera basis once,
        // instead of applying Java's hand-only motion on top of it. This is a rendering-space bridge,
        // not a weapon aim correction or an alternative camera controller.
        lease.cameraToSubmission.set(global).invert();
        poses.setIdentity();
        poses.mulPose(lease.cameraToSubmission);
        lease.requiresOwnedGeometry = !playerPoseAvailable;
        DRAWING.set(lease);
        return true;
    }

    static void end() { DRAWING.remove(); ROOT.remove(); }

    /** Called around one actual geometry draw, never during animation evaluation. */
    static Scope geometry(String identifier, UUID owner, AttachableQueryContext.LogicalHand hand, boolean firstPerson) {
        String previous = ROOT.get(); Lease lease = DRAWING.get();
        boolean owned = lease != null && firstPerson && lease.player.getUUID().equals(owner) && lease.hand == hand;
        String root = owned ? lease.geometryRoots.get(identifier) : null;
        if (owned && root == null && lease.requiresOwnedGeometry) {
            throw new IllegalStateException("Geometry requires a player pose or an owned camera root: " + identifier);
        }
        if (root != null && !lease.prepared) throw new IllegalStateException("Camera space has not been published");
        if (java.util.Objects.equals(previous, root)) return UNCHANGED;
        RootStack stack = ROOT_STACK.get();
        if (stack.depth == stack.frames.length) throw new IllegalStateException("Camera-space nesting limit");
        RootFrame frame = stack.frames[stack.depth];
        if (frame == null) stack.frames[stack.depth] = frame = new RootFrame(stack, stack.depth);
        frame.previous = previous; stack.depth++;
        if (root == null) ROOT.remove(); else ROOT.set(root);
        return frame;
    }

    /** Replace only a declared mesh root, after native local channels and before its descendants.
     * No inverse of an animated (possibly zero-scale) ancestor is used.
     */
    public static void applyBone(String name, boolean cubeGroup, PoseStack poses) {
        String root = ROOT.get();
        if (cubeGroup || root == null || !root.equals(name)) return;
        Lease lease = DRAWING.get();
        if (lease == null) return;
        poses.setIdentity(); poses.mulPose(lease.cameraToSubmission); poses.mulPose(lease.cameraRoot);
    }

    private static void check(double[] values, int size) {
        if (values == null || values.length != size) throw new IllegalArgumentException("Invalid camera-space vector");
        for (double value : values) if (!Double.isFinite(value) || Math.abs(value) > Float.MAX_VALUE) throw new IllegalArgumentException("Nonfinite camera-space value");
    }

    private static void requireThread() {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("View space mutations require client thread");
    }
}
