package org.oryxel.viabedrockutility.attachable;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import org.cube.converter.model.element.Parent;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;
import org.oryxel.viabedrockutility.renderer.BedrockPlayerModelMetadata;

import java.util.*;

/** Legacy shared skeletons inherit the actor pose before their own animation is applied.
 * Explicit root bindings and unrelated bone names retain independent attachable semantics.
 * The root itself is already carried by its physical anchor and must never be applied twice.
 */
final class HostBoneInheritance implements Runnable {
    private record Bone(IModelPart target, ModelPart targetPart, IModelPart source,
                        BedrockPlayerModelMetadata.Bone host, float bindX, float bindY, float bindZ) {}
    private final Bone[] bones;
    private final List<String> description;

    HostBoneInheritance(BedrockGeometryModel geometry, Model model, BedrockPlayerModelMetadata host) {
        Map<String, Parent> definitions = new HashMap<>();
        for (Parent bone : geometry.getParents()) definitions.put(key(bone.getName()), bone);
        Map<String, ModelPart> parts = new HashMap<>();
        for (ModelPart part : model.allParts()) {
            String name = ((IModelPart)(Object)part).viaBedrockUtility$getName();
            if (name != null) parts.putIfAbsent(key(name), part);
        }
        List<Bone> selected = new ArrayList<>();
        for (Parent bone : geometry.getParents()) {
            if (bone.getParent().isBlank()) continue;
            Parent root = bone;
            Set<String> seen = new HashSet<>();
            while (root != null && !root.getParent().isBlank() && seen.add(key(root.getName()))) {
                root = definitions.get(key(root.getParent()));
            }
            if (root == null || !root.getParent().isBlank() || !root.getBinding().isBlank()
                    || host.bone(root.getName()) == null) continue;
            BedrockPlayerModelMetadata.Bone source = host.bone(bone.getName());
            ModelPart target = parts.get(key(bone.getName()));
            if (source != null && target != null) selected.add(new Bone((IModelPart)(Object)target,
                    target, (IModelPart)(Object)source.part(), source, target.xRot, target.yRot, target.zRot));
        }
        bones = selected.toArray(Bone[]::new);
        description = selected.stream().map(bone -> bone.host.originalName() + "->" + bone.host.key()).toList();
    }

    List<String> description() { return description; }

    void release() {
        for (Bone bone : bones) {
            bone.targetPart.xRot = bone.bindX;
            bone.targetPart.yRot = bone.bindY;
            bone.targetPart.zRot = bone.bindZ;
        }
    }

    @Override public void run() {
        for (Bone bone : bones) {
            // Keep the geometry's bind pose; add only the host's animation contribution.
            bone.target.viaBedrockUtility$getRotation()
                    .add(bone.source.viaBedrockUtility$getRotation()).sub(bone.host.rotation());
            bone.target.viaBedrockUtility$getOffset().add(bone.source.viaBedrockUtility$getOffset());
            bone.target.viaBedrockUtility$setEntityRelativeRotation(bone.source.viaBedrockUtility$isEntityRelativeRotation());
            ModelPart source = bone.host.part();
            bone.targetPart.xRot = bone.bindX + source.xRot;
            bone.targetPart.yRot = bone.bindY + source.yRot;
            bone.targetPart.zRot = bone.bindZ + source.zRot;
            bone.targetPart.xScale *= source.xScale;
            bone.targetPart.yScale *= source.yScale;
            bone.targetPart.zScale *= source.zScale;
        }
    }

    private static String key(String value) { return value.toLowerCase(Locale.ROOT); }
}
