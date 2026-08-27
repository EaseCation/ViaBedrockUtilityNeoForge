package org.oryxel.viabedrockutility.animation;

import net.easecation.bedrockmotion.animation.vanilla.AnimateTransformation;
import net.easecation.bedrockmotion.animation.vanilla.AnimationHelper;
import net.easecation.bedrockmotion.mocha.LayeredScope;
import net.easecation.bedrockmotion.pack.definitions.AnimationDefinitions;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.adapter.McBoneModel;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;
import org.oryxel.viabedrockutility.mixin.interfaces.ICustomPlayerRendererHolder;
import org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.Function;
import team.unnamed.mocha.runtime.value.Value;

import java.util.*;

public class PlayerAnimationManager {
    private final Map<String, AnimationDefinitions.AnimationData> animations = new LinkedHashMap<>();
    private final Set<String> affectedBones = new HashSet<>();
    private final long startTimeMS = System.currentTimeMillis();
    private final Vector3f tempVec = new Vector3f();

    // Cached adapter for the player model. Reused across frames so the bone index (HashMap + bone
    // list, lazily built in McBoneModel) is constructed once instead of every frame. Mirrors
    // CustomEntityRenderer.boneModelCache. Re-created only when the underlying model swaps.
    private McBoneModel cachedBoneModel;

    // Reusable per-frame scope and query binding (mirrors CustomEntityRenderer.reusableFrameScope).
    // Avoids BASE_SCOPE.copy() (full CaseInsensitiveStringHashMap putAll) and a new MutableObjectBinding
    // every frame. LayeredScope layers only 'query'/'q' over the shared read-only BASE_SCOPE.
    private final LayeredScope reusableFrameScope = new LayeredScope(CustomEntityPayloadHandler.BASE_SCOPE);
    private final MutableObjectBinding reusableQuery = new MutableObjectBinding();
    private final MutableObjectBinding reusableVariables = new MutableObjectBinding();
    private String mainHandItemIdentifier = "";
    private String offHandItemIdentifier = "";
    private final Function<Object> isItemNameAny = (execution, arguments) -> {
        if (arguments.length() == 0) {
            return Value.of(false);
        }
        final String slot = arguments.next().eval().getAsString();
        final String expected = isOffHandSlot(slot) ? offHandItemIdentifier : mainHandItemIdentifier;
        for (int i = 1; i < arguments.length(); i++) {
            if (!expected.isEmpty() && expected.equalsIgnoreCase(arguments.next().eval().getAsString())) {
                return Value.of(true);
            }
        }
        return Value.of(false);
    };

    // Scratch set reused across frames to collect lowercased bone names driven by currently-playing
    // one-shot animations. Replaces a per-frame new HashSet<>() allocation.
    private final Set<String> scratchOnceBonesLC = new HashSet<>();

    // Looping skin slots are not all active at once. In particular, riding layers are additive and
    // must not affect a standing player. Reuse one set for the bones driven by active slots this frame.
    private final Set<String> scratchActiveBonesLC = new HashSet<>();

    // One-shot animations triggered at runtime (server AnimateEntityPacket on a player / humanoid NPC).
    // Each keeps its own start time, plays once and is pruned when it reaches its animation_length, and
    // (unlike the looping skin animations) does NOT register persistent affectedBones — its bones are
    // cleared transiently only while it is still playing, so vanilla locomotion resumes after it ends.
    private final Map<String, AnimationDefinitions.AnimationData> onceAnimations = new LinkedHashMap<>();
    private final Map<String, Long> onceStartMS = new HashMap<>();

    public void addAnimation(String shortName, AnimationDefinitions.AnimationData data) {
        // Skip empty overrides. A skin's resource_patch frequently maps an animation slot to an empty
        // placeholder animation; registering one contributes no keyframes, so it is a no-op we drop.
        if (data == null || data.compiled() == null) {
            return;
        }
        final Map<String, List<AnimateTransformation>> bones = data.compiled().boneAnimations();
        if (bones == null || bones.isEmpty()) {
            return;
        }
        boolean hasTransform = false;
        for (List<AnimateTransformation> list : bones.values()) {
            if (list != null && !list.isEmpty()) {
                hasTransform = true;
                break;
            }
        }
        if (!hasTransform) {
            return;
        }

        animations.put(shortName, data);
        // Store bone names lowercased: Bedrock bone names are case-insensitive, and clearVanillaRotation
        // uses HashSet.contains (O(1)) which needs the same normalization as the queried part name.
        for (String bone : bones.keySet()) {
            affectedBones.add(bone.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Trigger a one-shot named animation (e.g. animation.easecation.cheer) on this player/NPC. Re-triggering
     * the same name restarts it. It plays once and is removed when it reaches its animation_length.
     */
    public void playOnce(final String name, final AnimationDefinitions.AnimationData data) {
        if (data == null || data.compiled() == null) {
            return;
        }
        onceAnimations.put(name, data);
        onceStartMS.put(name, System.currentTimeMillis());
    }

    public boolean isEmpty() {
        return animations.isEmpty();
    }

    public Set<String> getAffectedBones() {
        return affectedBones;
    }

    /** Animation slot names that were actually registered (post-filtering). For diagnostics. */
    public Set<String> getRegisteredAnimationNames() {
        return animations.keySet();
    }

    /**
     * Called every frame from PlayerEntityModel.setAngles() TAIL injection.
     * Bedrock-authoritative: the skin ships its own animations, so they define the pose. We clear
     * vanilla's contribution on the bones those animations drive, then apply the Bedrock animations.
     */
    /**
     * Whether this player has any Bedrock animations (looping or one-shot). Used by
     * {@link org.oryxel.viabedrockutility.mixin.impl.render.PlayerEntityModelMixin} to decide whether
     * distance-based setupAnim throttling applies — vanilla-only players keep full-rate vanilla pose.
     */
    public boolean hasAnimations() {
        return !animations.isEmpty() || !onceAnimations.isEmpty();
    }

    public void animate(Model model, PlayerRenderState state) {
        if (cachedBoneModel == null || cachedBoneModel.getModel() != model) {
            cachedBoneModel = new McBoneModel(model);
        }
        final McBoneModel boneModel = cachedBoneModel;

        // Reset ALL bones' VBU pose to default before additive blending — mirrors the proven
        // custom-entity path (CustomEntityRenderer.render -> McBoneModel.resetAllBones). The animation
        // engine applies rotation/offset/scale ADDITIVELY (AnimateTransformation.Targets), so without
        // a full reset the transforms accumulate frame-over-frame and the pose drifts.
        boneModel.resetAllBones();

        // Clear vanilla setupAnim's rotation on every bone an active Bedrock animation drives. A Bedrock player
        // is posed entirely by Bedrock animations (humanoid_base_pose sets the neutral, locomotion layers
        // on top); there is no vanilla setupAnim in Bedrock. Since our VBU rotation is applied ADDITIVELY
        // on top of vanilla xRot/yRot/zRot at render (ModelPartMixin.translateAndRotate), leaving vanilla's
        // rotation in place double-poses the limbs (the splayed pose). Zeroing it on the driven bones makes
        // the Bedrock animation authoritative. Bones with no Bedrock animation keep their vanilla pose.
        // (For fully-custom-bone skins, vanilla never wrote these bones, so this is a harmless no-op.)
        final long now = System.currentTimeMillis();

        // Prune finished one-shot animations and collect the bones still-active ones drive this frame, so
        // their vanilla rotation is cleared only while playing.
        scratchOnceBonesLC.clear();
        if (!onceAnimations.isEmpty()) {
            final Iterator<Map.Entry<String, AnimationDefinitions.AnimationData>> it = onceAnimations.entrySet().iterator();
            while (it.hasNext()) {
                final Map.Entry<String, AnimationDefinitions.AnimationData> e = it.next();
                final float len = e.getValue().compiled().lengthInSeconds();
                final long onceElapsed = now - onceStartMS.getOrDefault(e.getKey(), now);
                if (len > 0 && onceElapsed / 1000F >= len) { // played to its animation_length -> remove
                    it.remove();
                    onceStartMS.remove(e.getKey());
                    continue;
                }
                final Map<String, List<AnimateTransformation>> bones = e.getValue().compiled().boneAnimations();
                if (bones != null && !bones.isEmpty()) {
                    for (String bone : bones.keySet()) {
                        scratchOnceBonesLC.add(bone.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }

        scratchActiveBonesLC.clear();
        for (Map.Entry<String, AnimationDefinitions.AnimationData> entry : animations.entrySet()) {
            if (isAnimationActive(entry.getKey(), state)) {
                collectAffectedBones(entry.getValue(), scratchActiveBonesLC);
            }
        }
        clearVanillaRotation(model, scratchActiveBonesLC, scratchOnceBonesLC);

        final Scope scope = buildScope(state);
        final long elapsed = now - startTimeMS;
        for (Map.Entry<String, AnimationDefinitions.AnimationData> entry : animations.entrySet()) {
            if (isAnimationActive(entry.getKey(), state)) {
                AnimationHelper.animate(scope, boneModel, entry.getValue().compiled(), elapsed, 1.0f, tempVec, null);
            }
        }
        // One-shot animations applied on top so the temporary action overrides the looping pose.
        for (Map.Entry<String, AnimationDefinitions.AnimationData> entry : onceAnimations.entrySet()) {
            final long onceElapsed = now - onceStartMS.getOrDefault(entry.getKey(), now);
            AnimationHelper.animate(scope, boneModel, entry.getValue().compiled(), onceElapsed, 1.0f, tempVec, null);
        }
    }

    /**
     * Zero vanilla setupAnim's xRot/yRot/zRot on bones driven by active looping or one-shot animations.
     * Both sets hold lowercased bone names; the part name is lowercased once per part and looked up via
     * HashSet.contains (O(1)) instead of an O(n) equalsIgnoreCase scan per bone per frame.
     */
    @SuppressWarnings("unchecked")
    private void clearVanillaRotation(Model model, Set<String> activeBonesLC, Set<String> transientBonesLC) {
        if ((activeBonesLC == null || activeBonesLC.isEmpty())
                && (transientBonesLC == null || transientBonesLC.isEmpty())) {
            return;
        }
        for (ModelPart part : (List<ModelPart>) model.allParts()) {
            final String name = ((IModelPart) (Object) part).viaBedrockUtility$getName();
            final String nameLC = name == null ? null : name.toLowerCase(Locale.ROOT);
            if (matchesAny(activeBonesLC, nameLC) || matchesAny(transientBonesLC, nameLC)) {
                part.xRot = 0.0F;
                part.yRot = 0.0F;
                part.zRot = 0.0F;
            }
        }
    }

    private static boolean matchesAny(final Set<String> bonesLC, final String nameLC) {
        return nameLC != null && bonesLC != null && !bonesLC.isEmpty() && bonesLC.contains(nameLC);
    }

    static boolean isAnimationActive(String shortName, PlayerRenderState state) {
        final String normalized = shortName.toLowerCase(Locale.ROOT);
        return !(normalized.equals("riding") || normalized.startsWith("riding.")) || state.isPassenger;
    }

    private static void collectAffectedBones(AnimationDefinitions.AnimationData animation, Set<String> target) {
        final Map<String, List<AnimateTransformation>> bones = animation.compiled().boneAnimations();
        if (bones == null) {
            return;
        }
        for (String bone : bones.keySet()) {
            target.add(bone.toLowerCase(Locale.ROOT));
        }
    }

    private Scope buildScope(PlayerRenderState state) {
        // Reuse a LayeredScope layered over the shared read-only BASE_SCOPE instead of deep-copying
        // BASE_SCOPE every frame (mirrors CustomEntityRenderer.buildFrameScope). Only 'query'/'q' are
        // written into the local layer; reads for 'math' fall through to BASE_SCOPE.
        reusableFrameScope.reset(CustomEntityPayloadHandler.BASE_SCOPE);

        // Reuse the query binding across frames. Every key below is re-set each frame (fixed set), so
        // we rely on set()'s overwrite semantics — no clear() is needed (MutableObjectBinding has none).
        final MutableObjectBinding query = reusableQuery;
        final MutableObjectBinding variables = reusableVariables;

        // Mirror CustomEntityRenderer.buildFrameScope so query-gated animation expressions evaluate
        // correctly. With only a handful of variables bound, terms that should resolve to ~0 when the
        // player is idle (e.g. limb-swing scaled by move speed) instead read a missing variable and
        // can leave a wrong residual constant — which, summed across all applied animations, produces
        // the fixed distorted pose. PlayerRenderState exposes fewer fields than the custom-entity
        // state, so values not available here are best-effort/zero.
        final float groundSpeed = state.walkAnimationSpeed;
        // LivingEntityRenderer already stores wrapDegrees(headYaw - bodyYaw) in state.yRot.
        // Subtracting bodyRot again leaks absolute world/body orientation into Bedrock's relative
        // target yaw, which makes the gun arms rotate when the third-person camera/player turns.
        final float headYaw = bedrockTargetYaw(state.yRot);
        final ICustomPlayerRendererHolder customState = state instanceof ICustomPlayerRendererHolder holder
                ? holder : null;
        final ResourceLocation mainHand = customState == null
                ? null : customState.viaBedrockUtility$getMainHandItemIdentifier();
        final ResourceLocation offHand = customState == null
                ? null : customState.viaBedrockUtility$getOffHandItemIdentifier();
        mainHandItemIdentifier = mainHand == null ? "" : mainHand.toString();
        offHandItemIdentifier = offHand == null ? "" : offHand.toString();

        query.set("life_time", Value.of(state.ageInTicks / 20.0f));
        query.set("modified_distance_moved", Value.of(state.walkAnimationPos));
        query.set("modified_move_speed", Value.of(groundSpeed));
        query.set("is_on_ground", Value.of(true));
        query.set("is_alive", Value.of(true));
        query.set("ground_speed", Value.of(groundSpeed));
        query.set("vertical_speed", Value.of(0.0));
        query.set("distance_from_camera", Value.of(Math.sqrt(state.distanceToCameraSq)));
        // Player geometry is expressed in Java model units (one Bedrock pixel is 1/16 block).
        // ZE's shared gun animation deliberately uses this value to select its Java-compatible pose.
        query.set("model_scale", Value.of(0.0625D));
        query.set("is_riding", Value.of(state.isPassenger));
        query.set("is_sneaking", Value.of(state.isCrouching));
        query.set("is_sleeping", Value.of(state.bedOrientation != null));
        query.set("is_item_name_any", isItemNameAny);

        // Rotation queries
        query.set("body_y_rotation", Value.of(state.bodyRot));
        query.set("body_x_rotation", Value.of(state.xRot));
        query.set("target_x_rotation", Value.of(state.xRot));
        query.set("target_y_rotation", Value.of(headYaw));
        query.set("head_x_rotation", Value.of(state.xRot));
        query.set("head_y_rotation", Value.of(headYaw));

        // Function-type queries (not derivable from PlayerRenderState — return 0 so lookups don't fail)
        query.setFunction("position_delta", (double arg) -> 0.0);
        query.setFunction("rotation_to_camera", (double arg) -> 0.0);

        final boolean mainHandHolding = !mainHandItemIdentifier.isEmpty();
        final boolean offHandHolding = !offHandItemIdentifier.isEmpty();
        final boolean rightHolding = state.mainArm == HumanoidArm.RIGHT
                ? mainHandHolding : offHandHolding;
        final boolean leftHolding = state.mainArm == HumanoidArm.LEFT
                ? mainHandHolding : offHandHolding;
        variables.set("gliding_speed_value", Value.of(1.0D));
        variables.set("tcos0", Value.of(computeTcos0(state.walkAnimationPos, groundSpeed)));
        variables.set("attack_time", Value.of(state.attackTime));
        variables.set("attack_body_rot_y", Value.of(computeAttackBodyYaw(state.attackTime)));
        variables.set("is_holding_right", Value.of(rightHolding));
        variables.set("is_holding_left", Value.of(leftHolding));
        variables.set("player_x_rotation", Value.of(state.xRot));

        reusableFrameScope.set("query", query);
        reusableFrameScope.set("q", query);
        reusableFrameScope.set("variable", variables);
        reusableFrameScope.set("v", variables);

        return reusableFrameScope;
    }

    static double computeTcos0(float modifiedDistanceMoved, float normalizedMoveSpeed) {
        return Math.cos(Math.toRadians(modifiedDistanceMoved * 38.17D))
                * normalizedMoveSpeed * 57.3D;
    }

    static double computeAttackBodyYaw(float attackTime) {
        return Math.sin(Math.toRadians(360.0D * Math.sqrt(Math.max(attackTime, 0.0F)))) * 5.0D;
    }

    static float bedrockTargetYaw(float javaRelativeHeadYaw) {
        return javaRelativeHeadYaw;
    }

    static boolean matchesItemNameAny(String slot, String mainHandIdentifier, String offHandIdentifier,
                                      Collection<String> candidates) {
        final String expected = isOffHandSlot(slot) ? offHandIdentifier : mainHandIdentifier;
        return expected != null && !expected.isEmpty() && candidates != null
                && candidates.stream().anyMatch(expected::equalsIgnoreCase);
    }

    private static boolean isOffHandSlot(String slot) {
        if (slot == null) {
            return false;
        }
        final String normalized = slot.toLowerCase(Locale.ROOT)
                .replace("_", "").replace(".", "").replace("-", "");
        return normalized.contains("offhand");
    }
}
