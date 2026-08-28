package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.oryxel.viabedrockutility.attachable.AttachableHostContext;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Carries Bedrock parent-bone deformation from the custom player mesh to flat vanilla armor. */
public final class BedrockPlayerArmorPose {
    private static final Map<ModelPart, Matrix4f> SOURCE_DEFORMATIONS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ModelPart, Matrix4f> ARMOR_PREFIXES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private BedrockPlayerArmorPose() {
    }

    public static void update(PlayerModel model) {
        final BedrockPlayerModelMetadata metadata = BedrockPlayerModelMetadata.get(model);
        if (metadata == null) {
            return;
        }
        final AttachableHostContext host = new AttachableHostContext(metadata);
        update(model.head, host, metadata.firstBone("head"));
        update(model.body, host, metadata.firstBone("body"));
        update(model.rightArm, host, metadata.firstBone("rightarm", "right_arm"));
        update(model.leftArm, host, metadata.firstBone("leftarm", "left_arm"));
        update(model.rightLeg, host, metadata.firstBone("rightleg", "right_leg"));
        update(model.leftLeg, host, metadata.firstBone("leftleg", "left_leg"));
    }

    public static void copy(ModelPart source, ModelPart target) {
        final Matrix4f deformation = SOURCE_DEFORMATIONS.get(source);
        if (deformation == null) {
            ARMOR_PREFIXES.remove(target);
        } else {
            ARMOR_PREFIXES.put(target, new Matrix4f(deformation));
        }
    }

    public static void apply(ModelPart part, PoseStack poses) {
        final Matrix4f prefix = ARMOR_PREFIXES.get(part);
        if (prefix != null) {
            poses.mulPose(prefix);
        }
    }

    private static void update(ModelPart part, AttachableHostContext host,
                               BedrockPlayerModelMetadata.Bone bone) {
        set(part, bone == null ? null : host.presentationDeformationMatrix(bone));
    }

    static void set(ModelPart part, Matrix4f deformation) {
        if (deformation == null) {
            SOURCE_DEFORMATIONS.remove(part);
        } else {
            SOURCE_DEFORMATIONS.put(part, new Matrix4f(deformation));
        }
    }
}
