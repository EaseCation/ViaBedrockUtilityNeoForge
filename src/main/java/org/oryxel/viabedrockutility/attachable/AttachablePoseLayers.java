package org.oryxel.viabedrockutility.attachable;

import net.easecation.bedrockmotion.attachable.AttachableAnimationRuntime;
import net.easecation.bedrockmotion.model.IBoneModel;
import net.easecation.bedrockmotion.model.IBoneTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.item.BedrockItemIdentity;
import java.util.*;

/** An equipment-owned numerical pose. It replaces the explicitly delegated TP animation stack only;
 * the source may retain named authored animations (unconditional, layered after the pose).
 * Rules, curve sampling and clocks belong to the producer; native inheritance/materials/locators stay here.
 */
public final class AttachablePoseLayers {
    @FunctionalInterface public interface BoneSink {
        void set(String bone, double[] position, double[] rotation, double[] scale);
    }
    public interface Source {
        Set<String> bones();
        void sample(BoneSink sink);
        /** Attachable animation identifiers kept playing natively on top of this pose (e.g. a part loop). */
        default Set<String> retainedAnimations() { return Set.of(); }
    }
    private record Key(UUID actor, AttachableQueryContext.LogicalHand hand) {}
    private static final Map<Key, Lease> ACTIVE = new HashMap<>();
    private AttachablePoseLayers() {}
    public static Lease acquire(AbstractClientPlayer player, String item, boolean offhand, Source source) {
        requireThread(); Objects.requireNonNull(source);
        Key key = new Key(player.getUUID(), offhand ? AttachableQueryContext.LogicalHand.OFF_HAND : AttachableQueryContext.LogicalHand.MAIN_HAND);
        Lease previous = ACTIVE.get(key);
        if (previous != null && previous.current()) throw new IllegalStateException("Attachable pose already owned");
        if (previous != null) previous.close();
        Lease lease = new Lease(key, player, item, source);
        if (!lease.current()) throw new IllegalStateException("Pose requires the actual held item");
        ACTIVE.put(key, lease); return lease;
    }
    static AttachableAnimationRuntime.PoseOverride current(Entity actor, String item, AttachableQueryContext.LogicalHand hand,
                                                           AttachableQueryContext.ViewContext view) {
        if (actor == null || view != AttachableQueryContext.ViewContext.THIRD_PERSON) return null;
        Lease lease = ACTIVE.get(new Key(actor.getUUID(), hand));
        return lease != null && lease.player == actor && lease.item.equals(item) && lease.current() ? lease : null;
    }
    static void tick() { for (Lease lease : List.copyOf(ACTIVE.values())) if (!lease.current()) lease.close(); }
    static void clearModels() { for (Lease lease : ACTIVE.values()) lease.models.clear(); }
    public static final class Lease implements AutoCloseable, AttachableAnimationRuntime.PoseOverride {
        private final Key key;
        private final AbstractClientPlayer player;
        private final String item;
        private final net.minecraft.resources.ResourceLocation itemId;
        private final Source source;
        private final Set<String> bones, retained;
        private final long connection, generation;
        private final IdentityHashMap<IBoneModel, Map<String, IBoneTarget>> models = new IdentityHashMap<>();
        private boolean closed;
        private Lease(Key key, AbstractClientPlayer player, String item, Source source) {
            this.key=key;this.player=player;this.item=item;this.source=source;
            itemId=net.minecraft.resources.ResourceLocation.parse(item);
            bones=Set.copyOf(source.bones());
            if(bones.isEmpty()||bones.size()>4096||bones.stream().anyMatch(String::isBlank))throw new IllegalArgumentException("Invalid numerical pose bones");
            retained=Set.copyOf(source.retainedAnimations());
            if(retained.size()>64||retained.stream().anyMatch(String::isBlank))throw new IllegalArgumentException("Invalid retained attachable animations");
            var host=ViaBedrockUtility.getInstance();connection=host.getConnectionEpoch();generation=host.getPackGeneration().generation();
        }
        public boolean isCurrent(){requireThread();return current();}
        private boolean current(){
            var mc=Minecraft.getInstance();var host=ViaBedrockUtility.getInstance();
            return !closed&&mc.level!=null&&player.level()==mc.level&&!player.isRemoved()&&mc.level.getEntity(player.getId())==player
                    &&host.getConnectionEpoch()==connection&&host.getPackGeneration().generation()==generation
                    &&itemId.equals(BedrockItemIdentity.resolve(key.hand==AttachableQueryContext.LogicalHand.MAIN_HAND?player.getMainHandItem():player.getOffhandItem()));
        }
        @Override public void apply(IBoneModel model){
            if(!current())throw new IllegalStateException("Retired numerical attachable pose");
            Map<String,IBoneTarget> targets=models.get(model);
            if(targets==null){
                if(models.size()>=128)throw new IllegalStateException("Too many native models in one pose scope");
                var index=model.getBoneIndex();var candidate=new HashMap<String,IBoneTarget>();
                for(String name:bones){var target=index.get(name);if(target==null)throw new IllegalArgumentException("Numerical pose bone missing: "+name);candidate.put(name,target);}
                targets=Map.copyOf(candidate);models.put(model,targets);
            }
            final Map<String,IBoneTarget> bound=targets;
            source.sample((name,position,rotation,scale)->{
                IBoneTarget target=bound.get(name);if(target==null)throw new IllegalArgumentException("Unowned numerical bone: "+name);
                finite(position);finite(rotation);finite(scale);
                target.resetForAnimationOverride();
                target.getOffset().add((float)position[0],(float)-position[1],(float)position[2]);
                target.getRotation().add((float)rotation[0],(float)rotation[1],(float)rotation[2]);
                target.setScale((float)scale[0],(float)scale[1],(float)scale[2]);
            });
        }
        @Override public Set<String> retainedAnimations(){return retained;}
        @Override public void close(){requireThread();if(closed)return;closed=true;ACTIVE.remove(key,this);models.clear();}
    }
    private static void finite(double[] vector){if(vector==null||vector.length!=3)throw new IllegalArgumentException("Expected pose vector");for(double value:vector)if(!Double.isFinite(value)||Math.abs(value)>Float.MAX_VALUE)throw new IllegalArgumentException("Nonfinite pose");}
    private static void requireThread(){if(!Minecraft.getInstance().isSameThread())throw new IllegalStateException("Pose ownership requires the client thread");}
}
