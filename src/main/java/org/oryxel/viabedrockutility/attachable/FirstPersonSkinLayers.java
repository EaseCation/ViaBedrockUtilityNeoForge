package org.oryxel.viabedrockutility.attachable;

import com.mojang.blaze3d.vertex.PoseStack;
import net.easecation.bedrockmotion.pack.PackManager;
import net.easecation.bedrockmotion.pack.definitions.AttachableDefinitions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import org.cube.converter.data.bedrock.BedrockAttachableData;
import org.cube.converter.data.bedrock.BedrockEntityData;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.item.BedrockItemIdentity;
import org.oryxel.viabedrockutility.renderer.BedrockPlayerModelMetadata;

import java.io.IOException;
import java.util.*;

/** Owned first-person geometry using the actor's skin and the common attachable renderer.
 * Pure animation only: action clocks, event ownership and gameplay stay with the caller.
 */
public final class FirstPersonSkinLayers {
    private static final Map<UUID, List<Lease>> ACTIVE = new HashMap<>();
    private FirstPersonSkinLayers() {}

    public record Spec(String animation, String condition, Map<String, String> geometries,
                       String renderController, String variantQuery, int classicIndex, int slimIndex) {
        public Spec { geometries = Map.copyOf(geometries); }
    }

    public static Program prepare(long connection, long generation, Spec spec) {
        requireThread();
        return new Program(connection, generation, spec);
    }

    public static final class Program implements AutoCloseable {
        private final long connection, generation;
        private final Spec spec;
        private final PackManager packs;
        private final AttachableDefinitions.AttachableDefinition definition;
        private boolean closed;

        private Program(long connection, long generation, Spec spec) {
            this.connection = connection; this.generation = generation; this.spec = spec;
            if (!current()) throw new IllegalStateException("Skin geometry resource generation is stale");
            packs = ViaBedrockUtility.getInstance().getPackManager();
            var animation = packs.getAnimationDefinitions().getAnimations().get(spec.animation());
            if (animation == null || !animation.animation().getTimeline().isEmpty()
                    || !animation.animation().getSoundEffects().isEmpty() || !animation.animation().getParticleEffects().isEmpty()) {
                throw new IllegalArgumentException("Skin layers require an existing event-free animation");
            }
            if (!spec.variantQuery().startsWith("query.mod.") || spec.geometries().isEmpty()) {
                throw new IllegalArgumentException("Skin layers require explicit geometry and variant bindings");
            }
            if (!packs.getRenderControllerDefinitions().getRenderControllers().containsKey(spec.renderController())) {
                throw new IllegalArgumentException("Missing skin render controller: " + spec.renderController());
            }
            for (String identifier : spec.geometries().values()) {
                var geometry = packs.getModelDefinitions().getEntityModels().get(identifier);
                if (geometry == null) throw new IllegalArgumentException("Missing skin geometry: " + identifier);
                if (geometry.getParents().stream().anyMatch(bone -> !bone.getTextureMeshes().isEmpty())) {
                    throw new IllegalArgumentException("Skin texture_mesh requires a texture-specific geometry compiler");
                }
            }
            var scripts = new BedrockEntityData.Scripts(List.of(), List.of(),
                    new BedrockEntityData.Scripts.Scale("1", "1", "1", "1"),
                    List.of(new BedrockEntityData.Scripts.Animate("pose", spec.condition())));
            var data = new BedrockAttachableData("vbu:owned_skin_layer", scripts,
                    List.of(new BedrockEntityData.RenderController(spec.renderController(), spec.condition())),
                    Map.of("default", "entity_alphatest"), Map.of("pose", spec.animation()),
                    Map.of("default", "vbu:owner_skin"), spec.geometries(), Map.of());
            definition = new AttachableDefinitions.AttachableDefinition(data.getIdentifier(), data, "owned_skin_layer", 0);
        }

        private boolean current() {
            var host = ViaBedrockUtility.getInstance();
            return !closed && host.getConnectionEpoch() == connection && host.getPackGeneration().generation() == generation;
        }

        public Lease acquire(AbstractClientPlayer player, String itemIdentifier, boolean offhand) {
            requireThread();
            if (!current() || Minecraft.getInstance().level != player.level() || player.isRemoved()) {
                throw new IllegalStateException("Skin geometry scope is stale");
            }
            var scopes = ACTIVE.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>());
            scopes.removeIf(scope -> {
                if (scope.isCurrent()) return false;
                scope.released = true; return true;
            });
            if (scopes.size() >= 8) throw new IllegalStateException("Too many owned skin layers for one player");
            Lease lease = new Lease(this, player, itemIdentifier, offhand);
            scopes.add(lease);
            return lease;
        }

        @Override public void close() {
            requireThread();
            if (closed) return;
            closed = true;
            for (var scopes : ACTIVE.values()) scopes.removeIf(scope -> {
                if (scope.program != this) return false;
                scope.released = true; return true;
            });
            ACTIVE.values().removeIf(List::isEmpty);
        }
    }

    static void tick() {
        for (var scopes : ACTIVE.values()) scopes.removeIf(scope -> {
            if (scope.isCurrent()) return false;
            scope.released = true; return true;
        });
        ACTIVE.values().removeIf(List::isEmpty);
    }

    public static final class Lease implements AutoCloseable {
        private final Program program;
        private final AbstractClientPlayer player;
        private final String itemIdentifier;
        private final boolean offhand;
        private final AttachableRuntimeInstance runtime;
        private boolean released;
        private Lease(Program program, AbstractClientPlayer player, String itemIdentifier, boolean offhand) {
            this.program = program; this.player = player; this.itemIdentifier = itemIdentifier; this.offhand = offhand;
            runtime = new AttachableRuntimeInstance(program.definition, program.packs, itemIdentifier);
        }
        public boolean isCurrent() {
            var level = Minecraft.getInstance().level;
            if (released || !program.current() || level == null || player.level() != level || player.isRemoved()
                    || level.getPlayerByUUID(player.getUUID()) != player) return false;
            var item = BedrockItemIdentity.resolve(offhand ? player.getOffhandItem() : player.getMainHandItem());
            return item != null && item.toString().equals(itemIdentifier);
        }
        @Override public void close() {
            requireThread();
            released = true;
            var scopes = ACTIVE.get(player.getUUID());
            if (scopes != null) { scopes.remove(this); if (scopes.isEmpty()) ACTIVE.remove(player.getUUID()); }
        }
    }

    static void render(AttachableOwnerSnapshot owner, AttachableItemSnapshot item, AttachableQueryContext.LogicalHand hand,
                       HumanoidArm arm, PlayerModel model, ResourceLocation skin, PoseStack poses,
                       MultiBufferSource buffers, int light, float partialTick) throws IOException {
        var scopes = ACTIVE.get(owner.uuid());
        if (scopes == null) return;
        var metadata = BedrockPlayerModelMetadata.get(model);
        if (metadata == null) throw new IllegalStateException("Skin layer requires the current Bedrock player skeleton");
        var host = new AttachableHostContext(metadata);
        var iterator = scopes.iterator();
        while (iterator.hasNext()) {
            Lease lease = iterator.next();
            if (!lease.isCurrent()) { lease.released = true; iterator.remove(); continue; }
            if (lease.offhand != (hand == AttachableQueryContext.LogicalHand.OFF_HAND)) continue;
            long tick = lease.player.tickCount;
            lease.runtime.update(owner, lease.player, item, hand, arm, AttachableQueryContext.ViewContext.FIRST_PERSON, tick, partialTick);
            lease.runtime.skinContext(lease.program.spec.variantQuery(), metadata.slim()
                    ? lease.program.spec.slimIndex() : lease.program.spec.classicIndex());
            lease.runtime.prepareFrame(tick, partialTick);
            lease.runtime.advanceCurrentFrameTo(tick);
            lease.runtime.render(host, poses, buffers, light, partialTick, () -> {}, skin);
        }
        if (scopes.isEmpty()) ACTIVE.remove(owner.uuid());
    }

    private static void requireThread() {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Skin layer mutations require client thread");
    }
}
