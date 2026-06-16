package org.oryxel.viabedrockutility.animation;

import net.easecation.bedrockmotion.animation.vanilla.AnimateTransformation;
import net.easecation.bedrockmotion.animation.vanilla.AnimationHelper;
import net.easecation.bedrockmotion.pack.definitions.AnimationDefinitions;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.adapter.McBoneModel;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;
import org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.Value;

import java.util.*;

public class PlayerAnimationManager {
    private final Map<String, AnimationDefinitions.AnimationData> animations = new LinkedHashMap<>();
    private final Set<String> affectedBones = new HashSet<>();
    private final long startTimeMS = System.currentTimeMillis();
    private final Vector3f tempVec = new Vector3f();

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
        affectedBones.addAll(bones.keySet());
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
    public void animate(Model model, PlayerRenderState state) {
        final McBoneModel boneModel = new McBoneModel(model);

        // Reset ALL bones' VBU pose to default before additive blending — mirrors the proven
        // custom-entity path (CustomEntityRenderer.render -> McBoneModel.resetAllBones). The animation
        // engine applies rotation/offset/scale ADDITIVELY (AnimateTransformation.Targets), so without
        // a full reset the transforms accumulate frame-over-frame and the pose drifts.
        boneModel.resetAllBones();

        // Clear vanilla setupAnim's rotation on every bone a Bedrock animation drives. A Bedrock player
        // is posed entirely by Bedrock animations (humanoid_base_pose sets the neutral, locomotion layers
        // on top); there is no vanilla setupAnim in Bedrock. Since our VBU rotation is applied ADDITIVELY
        // on top of vanilla xRot/yRot/zRot at render (ModelPartMixin.translateAndRotate), leaving vanilla's
        // rotation in place double-poses the limbs (the splayed pose). Zeroing it on the driven bones makes
        // the Bedrock animation authoritative. Bones with no Bedrock animation keep their vanilla pose.
        // (For fully-custom-bone skins, vanilla never wrote these bones, so this is a harmless no-op.)
        clearVanillaRotation(model);

        final Scope scope = buildScope(state);
        final long elapsed = System.currentTimeMillis() - startTimeMS;
        for (Map.Entry<String, AnimationDefinitions.AnimationData> entry : animations.entrySet()) {
            AnimationHelper.animate(scope, boneModel, entry.getValue().compiled(), elapsed, 1.0f, tempVec, null);
        }
    }

    /** Zero vanilla setupAnim's xRot/yRot/zRot on the bones driven by registered Bedrock animations. */
    @SuppressWarnings("unchecked")
    private void clearVanillaRotation(Model model) {
        if (affectedBones.isEmpty()) {
            return;
        }
        for (ModelPart part : (List<ModelPart>) model.allParts()) {
            final String name = ((IModelPart) (Object) part).viaBedrockUtility$getName();
            for (String affected : affectedBones) {
                if (affected.equalsIgnoreCase(name)) {
                    part.xRot = 0.0F;
                    part.yRot = 0.0F;
                    part.zRot = 0.0F;
                    break;
                }
            }
        }
    }

    private Scope buildScope(PlayerRenderState state) {
        // Start from BASE_SCOPE (contains math binding) to get math.cos, math.sin, etc.
        final Scope scope = CustomEntityPayloadHandler.BASE_SCOPE.copy();

        final MutableObjectBinding query = new MutableObjectBinding();

        // Mirror CustomEntityRenderer.buildFrameScope so query-gated animation expressions evaluate
        // correctly. With only a handful of variables bound, terms that should resolve to ~0 when the
        // player is idle (e.g. limb-swing scaled by move speed) instead read a missing variable and
        // can leave a wrong residual constant — which, summed across all applied animations, produces
        // the fixed distorted pose. PlayerRenderState exposes fewer fields than the custom-entity
        // state, so values not available here are best-effort/zero.
        final float groundSpeed = state.walkAnimationSpeed;
        final float headYaw = state.yRot - state.bodyRot; // head yaw relative to body

        query.set("life_time", Value.of(state.ageInTicks / 20.0f));
        query.set("modified_distance_moved", Value.of(state.walkAnimationPos));
        query.set("modified_move_speed", Value.of(groundSpeed));
        query.set("is_on_ground", Value.of(true));
        query.set("is_alive", Value.of(true));
        query.set("ground_speed", Value.of(groundSpeed));
        query.set("vertical_speed", Value.of(0.0));
        query.set("distance_from_camera", Value.of(Math.sqrt(state.distanceToCameraSq)));

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

        scope.set("query", query);
        scope.set("q", query);

        return scope;
    }
}
