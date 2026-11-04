package org.oryxel.viabedrockutility.animation;

import net.easecation.bedrockmotion.animator.AnimationClock;
import net.easecation.bedrockmotion.animator.Animator;
import net.easecation.bedrockmotion.model.AnimationEventListener;
import net.easecation.bedrockmotion.mocha.MoLangEngine;
import net.easecation.bedrockmotion.mocha.MoLangEvaluationContext;
import net.easecation.bedrockmotion.pack.definitions.AnimationDefinitions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.adapter.McBoneModel;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.value.StructuredObjectBinding;
import team.unnamed.mocha.runtime.value.Value;

import java.io.IOException;
import java.util.*;

/** Owned, event-free local player pose layers. Never replaces the player's base controller. */
public final class PlayerPoseLayers {
    private static final Map<UUID, List<Lease>> ACTIVE = new HashMap<>();
    private static final Map<PlayerModel, ModelState> APPLIED = new WeakHashMap<>();

    public static boolean hasActive(UUID player) { return ACTIVE.containsKey(player); }

    private PlayerPoseLayers() {}

    /** Prepared expressions are immutable and shared; variables and clocks belong to a lease/view. */
    public record Step(String animation, String condition) {}

    public record Layer(String animation, String condition, List<Step> steps,
                        Map<String, String> nativeRotationOwners) {
        public Layer {
            steps = List.copyOf(steps);
            nativeRotationOwners = Map.copyOf(nativeRotationOwners);
        }
    }

    public static Program prepare(long connection, long generation, Layer firstPerson, Layer thirdPerson,
                                  Map<String, Double> parameters) {
        requireThread();
        return new Program(connection, generation, firstPerson, thirdPerson, parameters);
    }

    @FunctionalInterface public interface PoseSink {
        void set(String bone, double xDegrees, double yDegrees, double zDegrees, double xScale, double yScale, double zScale, boolean entityRelative);
    }
    @FunctionalInterface public interface PoseSource {
        void sample(float nativePitch, float nativeYaw, PoseSink sink);
    }
    /** A numerical owner supplies finished rotations and local scales; VBU only owns native application/restoration. */
    public static Program preparePose(long connection, long generation, Set<String> bones, Map<String,Double> parameters) {
        requireThread();
        if (bones.isEmpty() || bones.size() > 64) throw new IllegalArgumentException("Invalid numerical rotation ownership");
        return new Program(connection, generation, null, null, parameters, bones);
    }

    public static final class Program implements AutoCloseable {
        private final long connection, generation;
        private final Prepared first, third;
        private final Map<String, Double> defaults;
        private boolean closed;

        private Program(long connection, long generation, Layer first, Layer third, Map<String, Double> parameters) {
            this(connection, generation, first, third, parameters, null);
        }
        private Program(long connection, long generation, Layer first, Layer third, Map<String, Double> parameters, Set<String> rotations) {
            this.connection = connection;
            this.generation = generation;
            if (!current()) throw new IllegalStateException("Player pose resource generation is stale");
            var normalized = new LinkedHashMap<String, Double>();
            parameters.forEach((key, value) -> {
                String name = normalize(key);
                if (!name.startsWith("mod.") || value == null || !Double.isFinite(value)) {
                    throw new IllegalArgumentException("Player pose parameters require finite query.mod values");
                }
                if (normalized.putIfAbsent(name, value) != null) throw new IllegalArgumentException("Duplicate query " + key);
            });
            defaults = Map.copyOf(normalized);
            this.first = compile(first);
            this.third = rotations == null ? compile(third) : new Prepared(null, null, List.of(),
                    rotations.stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet()), Map.of());
        }

        private boolean current() {
            var host = ViaBedrockUtility.getInstance();
            return !closed && host.getConnectionEpoch() == connection
                    && host.getPackGeneration().generation() == generation;
        }

        private Prepared compile(Layer layer) {
            if (layer == null) return null;
            var data = animation(layer.animation());
            if (layer.steps().size() > 512) throw new IllegalArgumentException("Too many player pose steps");
            try {
                List<PreparedStep> steps = new ArrayList<>();
                Set<String> bones = new HashSet<>(data.compiled().boneAnimations().keySet());
                for (Step step : layer.steps()) {
                    var source = animation(step.animation());
                    steps.add(new PreparedStep(source, MoLangEngine.compile(step.condition())));
                    bones.addAll(source.compiled().boneAnimations().keySet());
                }
                Map<String, MoLangEngine.CompiledExpression> owners = new LinkedHashMap<>();
                for (var entry : layer.nativeRotationOwners().entrySet()) {
                    String name = entry.getKey().toLowerCase(Locale.ROOT);
                    if (!data.compiled().boneAnimations().containsKey(name)) {
                        throw new IllegalArgumentException("Rotation owner not in animation: " + name);
                    }
                    owners.put(name, MoLangEngine.compile(entry.getValue()));
                }
                return new Prepared(data, MoLangEngine.compile(layer.condition()), List.copyOf(steps),
                        Set.copyOf(bones), Map.copyOf(owners));
            } catch (IOException error) {
                throw new IllegalArgumentException("Invalid player pose expressions: " + layer.animation(), error);
            }
        }

        private AnimationDefinitions.AnimationData animation(String identifier) {
            var manager = ViaBedrockUtility.getInstance().getPackManager();
            var data = manager == null ? null : manager.getAnimationDefinitions().getAnimations().get(identifier);
            if (data == null) throw new IllegalArgumentException("Missing player pose animation " + identifier);
            if (!data.animation().getTimeline().isEmpty() || !data.animation().getParticleEffects().isEmpty()
                    || !data.animation().getSoundEffects().isEmpty()) {
                throw new IllegalArgumentException("Player pose layers cannot own animation events: " + identifier);
            }
            return data;
        }

        public Lease acquire(AbstractClientPlayer player) {
            requireThread();
            if (!current() || Minecraft.getInstance().level != player.level() || player.isRemoved()) {
                throw new IllegalStateException("Cannot acquire a stale player pose scope");
            }
            var scopes = ACTIVE.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>());
            scopes.removeIf(scope -> !scope.current());
            for (Lease scope : scopes) {
                if (overlaps(first, scope.program.first) || overlaps(third, scope.program.third)) {
                    throw new IllegalStateException("Player pose channels already have an owner");
                }
            }
            Lease lease = new Lease(this, player);
            scopes.add(lease);
            return lease;
        }

        @Override public void close() {
            requireThread();
            if (closed) return;
            closed = true;
            for (List<Lease> scopes : ACTIVE.values()) scopes.removeIf(scope -> {
                if (scope.program != this) return false;
                scope.released = true;
                return true;
            });
            ACTIVE.values().removeIf(List::isEmpty);
        }
    }

    private record PreparedStep(AnimationDefinitions.AnimationData data, MoLangEngine.CompiledExpression condition) {}

    private record Prepared(AnimationDefinitions.AnimationData data, MoLangEngine.CompiledExpression condition,
                            List<PreparedStep> steps, Set<String> bones,
                            Map<String, MoLangEngine.CompiledExpression> nativeRotationOwners) {}

    public static final class Lease implements AutoCloseable {
        private final Program program;
        private final AbstractClientPlayer player;
        private final PlayerAnimationOwner owner;
        private final StructuredObjectBinding parameters = new StructuredObjectBinding();
        private final PlayerAnimationRuntime.MovementDistance distance = new PlayerAnimationRuntime.MovementDistance();
        private final View first, third;
        private boolean released;
        private long revision;
        private PoseSource poseSource;
        public void publishPose(PoseSource source) {
            requireThread();
            if (!current() || program.third == null || program.third.data != null) throw new IllegalStateException("Not a current numerical rotation owner");
            poseSource = Objects.requireNonNull(source); revision++;
        }

        private Lease(Program program, AbstractClientPlayer player) {
            this.program = program;
            this.player = player;
            owner = new PlayerAnimationOwner(player, player.level());
            program.defaults.forEach((key, value) -> parameters.setPath(key, value.doubleValue()));
            first = program.first == null ? null : new View(program.first, parameters);
            third = program.third == null ? null : new View(program.third, parameters);
        }

        public boolean isCurrent() { return current(); }

        private boolean current() {
            return !released && program.current() && Minecraft.getInstance().level == player.level()
                    && !player.isRemoved() && player.level().getEntity(player.getId()) == player;
        }

        /** Atomic update of declared parameters; no resource registration or AST parsing. */
        public void publish(Map<String, Double> values) {
            requireThread();
            if (!current()) throw new IllegalStateException("Player pose scope is retired");
            for (var entry : values.entrySet()) {
                if (!program.defaults.containsKey(normalize(entry.getKey())) || entry.getValue() == null
                        || !Double.isFinite(entry.getValue())) throw new IllegalArgumentException("Invalid player pose query " + entry.getKey());
            }
            boolean changed = false;
            for (var entry : values.entrySet()) {
                String name = normalize(entry.getKey());
                if (parameters.getPath(name).getAsNumber() != entry.getValue()) {
                    parameters.setPath(name, entry.getValue().doubleValue());
                    changed = true;
                }
            }
            if (changed) revision++;
        }

        /** Bounded read-only evidence; never evaluates a pose or advances its clock. */
        public Map<String, Object> inspect(boolean firstPerson, List<String> names) {
            requireThread();
            if (names.size() > 32) throw new IllegalArgumentException("At most 32 variables");
            View view = firstPerson ? first : third;
            Map<String, Double> selected = new LinkedHashMap<>();
            if (view != null) for (String raw : names) {
                String name = raw.startsWith("variable.") ? raw.substring(9) : raw;
                if (name.length() > 256) throw new IllegalArgumentException("Variable name too long");
                Value value = view.inputs.variables.get(name);
                if (!value.isNil()) selected.put(raw, value.getAsNumber());
            }
            Map<String, Object> pose = new LinkedHashMap<>();
            if (view != null && view.parts != null) for (ModelPart part : view.parts) {
                IModelPart extension = (IModelPart)(Object)part;
                if (extension.viaBedrockUtility$isCubeGroup() || pose.size() >= 8) continue;
                Vector3f rotation = extension.viaBedrockUtility$getRotation();
                pose.put(extension.viaBedrockUtility$getName(), Map.of(
                        "native_radians", List.of(part.xRot, part.yRot, part.zRot),
                        "bedrock_degrees", List.of(rotation.x, rotation.y, rotation.z),
                        "scale", List.of(part.xScale, part.yScale, part.zScale),
                        "bedrock_offset", List.of(extension.viaBedrockUtility$getOffset().x,
                                -extension.viaBedrockUtility$getOffset().y, extension.viaBedrockUtility$getOffset().z)));
            }
            return Map.of("pose", pose, "current", current(), "failure", view == null ? "" : view.failure, "variables", selected,
                    "display_frame", view == null ? -1L : view.displayFrame,
                    "prepared_frames", view == null ? 0L : view.preparedFrames);
        }

        @Override public void close() {
            requireThread();
            if (released) return;
            released = true;
            List<Lease> scopes = ACTIVE.get(player.getUUID());
            if (scopes != null) {
                scopes.remove(this);
                if (scopes.isEmpty()) ACTIVE.remove(player.getUUID());
            }
        }
    }

    private static final class View {
        private final Prepared prepared;
        private final PlayerAnimationInputs inputs = new PlayerAnimationInputs();
        private final AnimationClock.Client clock = new AnimationClock.Client();
        private final Animator animator;
        private final List<Animator> stepAnimators;
        private McBoneModel model;
        private List<ModelPart> parts;
        private final Set<String> replacementRotations = new HashSet<>();
        private long preparedFrames;
        private long displayFrame = Long.MIN_VALUE;
        private boolean enabled;
        private String failure = "";
        private PlayerModel failedModel;
        private long failedRevision = Long.MIN_VALUE;

        private View(Prepared prepared, StructuredObjectBinding parameters) {
            this.prepared = prepared;
            inputs.query.set("mod", parameters.get("mod"));
            if (prepared.data == null) { animator = null; stepAnimators = List.of(); return; }
            AnimationEventListener listener = new AnimationEventListener() {
                @Override public void onTimelineEvent(List<String> expressions) {}
                @Override public Scope getEntityScope() { return inputs.scope; }
            };
            stepAnimators = prepared.steps.stream().map(step -> {
                Animator entry = new Animator(listener, step.data, clock);
                entry.setBaseScope(inputs.scope);
                return entry;
            }).toList();
            animator = new Animator(listener, prepared.data, clock);
            animator.setBaseScope(inputs.scope);
        }

        private boolean samplePose(PlayerModel target, PlayerAnimationState state, Lease lease) {
            if (lease.poseSource == null) return false;
            ModelState saved = APPLIED.computeIfAbsent(target, ignored -> new ModelState());
            int start = saved.active.size();
            try {
                lease.poseSource.sample(state.targetXRotation(), state.targetYRotation(), (bone, x, y, z, sx, sy, sz, relative) -> {
                    if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                            || Math.abs(x) > Float.MAX_VALUE || Math.abs(y) > Float.MAX_VALUE || Math.abs(z) > Float.MAX_VALUE) {
                        throw new IllegalArgumentException("Nonfinite numerical player rotation");
                    }
                    if (!Double.isFinite(sx) || !Double.isFinite(sy) || !Double.isFinite(sz)
                            || sx <= 0 || sy <= 0 || sz <= 0
                            || sx > Float.MAX_VALUE || sy > Float.MAX_VALUE || sz > Float.MAX_VALUE)
                        throw new IllegalArgumentException("Invalid numerical player scale");
                    ModelPart selected = null;
                    for (ModelPart part : parts) if (((IModelPart)(Object)part).viaBedrockUtility$getName().equalsIgnoreCase(bone)) { selected = part; break; }
                    if (selected == null) throw new IllegalArgumentException("Unowned player rotation: " + bone);
                    for (int i = start; i < saved.active.size(); i++) if (saved.active.get(i).part == selected) throw new IllegalArgumentException("Duplicate player rotation: " + bone);
                    saved.capture(selected);
                    selected.xRot = selected.yRot = selected.zRot = 0;
                    selected.xScale = (float)sx; selected.yScale = (float)sy; selected.zScale = (float)sz;
                    IModelPart extension = (IModelPart)(Object)selected;
                    extension.viaBedrockUtility$getRotation().set((float)x, (float)y, (float)z);
                    extension.viaBedrockUtility$setEntityRelativeRotation(relative);
                });
                preparedFrames++; displayFrame = BedrockFrameTime.INSTANCE.frame();
                return saved.active.size() > start;
            } catch (RuntimeException failure) {
                for (int i = saved.active.size() - 1; i >= start; i--) saved.active.remove(i).restore();
                throw failure;
            }
        }

        private boolean sample(PlayerModel target, PlayerAnimationState state, Lease lease) throws IOException {
            if (model == null || model.getModel() != target) {
                McBoneModel candidate = new McBoneModel(target);
                if (!candidate.getBoneIndex().keySet().containsAll(prepared.bones)) {
                    throw new IllegalArgumentException("Player skeleton lacks pose bones " + prepared.bones);
                }
                model = candidate;
                inputs.bindModel(target);
                parts = target.allParts().stream().filter(part -> prepared.bones.contains(
                        Objects.toString(((IModelPart)(Object)part).viaBedrockUtility$getName(), "").toLowerCase(Locale.ROOT))).toList();
            }
            if (prepared.data == null) return samplePose(target, state, lease);
            if (displayFrame != BedrockFrameTime.INSTANCE.frame()) {
                clock.advanceTick(state.tick());
                clock.sample(state.partialTick());
                inputs.update(state, lease.distance.update(state));
                // Prepare conditions once per display frame, in declaration order.
                // Bones are sampled below in that same order, before the final layer.
                for (int i = 0; i < prepared.steps.size(); i++) {
                    double weight = MoLangEngine.eval(inputs.scope, prepared.steps.get(i).condition).getAsNumber();
                    if (!Double.isFinite(weight) || Math.abs(weight) > Float.MAX_VALUE) {
                        throw new IllegalArgumentException("Nonfinite player pose step weight");
                    }
                    stepAnimators.get(i).setBlendWeight((float)weight);
                }
                enabled = MoLangEngine.eval(inputs.scope, prepared.condition).getAsBoolean();
                replacementRotations.clear();
                if (enabled) {
                    for (var entry : prepared.nativeRotationOwners.entrySet()) {
                        if (MoLangEngine.eval(inputs.scope, entry.getValue()).getAsBoolean()) replacementRotations.add(entry.getKey());
                    }
                    for (Animator entry : stepAnimators) entry.advance();
                    animator.advance();
                }
                preparedFrames++;
                displayFrame = BedrockFrameTime.INSTANCE.frame();
            }
            if (!enabled) return false;
            ModelState saved = APPLIED.computeIfAbsent(target, ignored -> new ModelState());
            int start = saved.active.size();
            for (ModelPart part : parts) {
                saved.capture(part);
                // The caller explicitly transfers these native channels to this pose layer.
                // Do not clear unrelated body/limb motion or use this to emulate relative_to.
                if (replacementRotations.contains(((IModelPart)(Object)part).viaBedrockUtility$getName().toLowerCase(Locale.ROOT))) {
                    part.xRot = part.yRot = part.zRot = 0.0F;
                }
            }
            try {
                for (Animator entry : stepAnimators) {
                    if (entry.getBlendWeight() > 0) entry.animate(model, false);
                }
                animator.animate(model, false);
            } catch (IOException | RuntimeException error) {
                for (int i = saved.active.size() - 1; i >= start; i--) saved.active.remove(i).restore();
                throw error;
            }
            return true;
        }
    }

    /** Restore only our previous contribution before the host computes the next base pose. */
    public static void begin(PlayerModel model) {
        ModelState previous = APPLIED.get(model);
        if (previous != null && !previous.active.isEmpty()) {
            previous.active.forEach(SavedPart::restore);
            previous.active.clear();
            previous.restored = true;
        }
    }

    /** Called after the host's normal pose, before armor, held items and pose publication. */
    public static boolean sample(PlayerModel model, PlayerAnimationState state) {
        ModelState previous = APPLIED.get(model);
        boolean changed = previous != null && previous.restored;
        if (previous != null) previous.restored = false;
        if (state == null) return changed;
        List<Lease> scopes = ACTIVE.get(state.playerUuid());
        if (scopes == null) return changed;
        for (Lease lease : scopes) {
            if (!lease.current() || !lease.owner.hasSameInstances(state.owner())) continue;
            View view = state.view() == PlayerAnimationState.View.FIRST_PERSON ? lease.first : lease.third;
            if (view == null || view.failedModel == model && view.failedRevision == lease.revision) continue;
            try {
                changed |= view.sample(model, state, lease);
                view.failure = "";
            } catch (IOException | RuntimeException error) {
                view.failedModel = model;
                view.failedRevision = lease.revision;
                String message = error.getClass().getSimpleName() + ": " + error.getMessage();
                view.failure = message.substring(0, Math.min(message.length(), 512));
            }
        }
        return changed;
    }

    private static final class ModelState {
        final Map<ModelPart, SavedPart> pool = new IdentityHashMap<>();
        final List<SavedPart> active = new ArrayList<>();
        boolean restored;
        void capture(ModelPart part) {
            SavedPart saved = pool.computeIfAbsent(part, SavedPart::new);
            saved.capture();
            active.add(saved);
        }
    }

    private static final class SavedPart {
        private final ModelPart part;
        private final Vector3f rotation = new Vector3f(), offset = new Vector3f();
        private float sx, sy, sz, rx, ry, rz, x, y, z;
        private boolean entityRelativeRotation;
        SavedPart(ModelPart part) { this.part = part; }
        void capture() {
            IModelPart extension = (IModelPart)(Object)part;
            rotation.set(extension.viaBedrockUtility$getRotation());
            offset.set(extension.viaBedrockUtility$getOffset());
            sx = part.xScale; sy = part.yScale; sz = part.zScale;
            rx = part.xRot; ry = part.yRot; rz = part.zRot;
            x = part.x; y = part.y; z = part.z;
            entityRelativeRotation = extension.viaBedrockUtility$isEntityRelativeRotation();
        }
        void restore() {
            IModelPart extension = (IModelPart)(Object)part;
            extension.viaBedrockUtility$getRotation().set(rotation);
            extension.viaBedrockUtility$getOffset().set(offset);
            part.xScale = sx; part.yScale = sy; part.zScale = sz;
            part.xRot = rx; part.yRot = ry; part.zRot = rz;
            part.x = x; part.y = y; part.z = z;
            extension.viaBedrockUtility$setEntityRelativeRotation(entityRelativeRotation);
        }
    }

    private static boolean overlaps(Prepared a, Prepared b) {
        return a != null && b != null && !Collections.disjoint(a.bones, b.bones);
    }

    private static String normalize(String name) {
        return org.oryxel.viabedrockutility.attachable.AttachableQueryProviders.normalize(name);
    }

    private static void requireThread() {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Player pose mutations require client thread");
    }
}
