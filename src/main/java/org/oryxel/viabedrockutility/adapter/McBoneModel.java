package org.oryxel.viabedrockutility.adapter;

import net.easecation.bedrockmotion.model.IBoneModel;
import net.easecation.bedrockmotion.model.IBoneTarget;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;

import java.util.*;

/**
 * Adapter: wraps any MC Model as IBoneModel.
 * Lazily builds a flat bone index from Model.getParts() traversal.
 */
public class McBoneModel implements IBoneModel {
    private final Model model;
    private final ModelPart[] modelParts;
    private Map<String, IBoneTarget> boneIndex;
    private List<IBoneTarget> allBones;

    public McBoneModel(Model model) {
        this.model = model;
        // Model.allParts() is stable after model construction. Snapshot it once so every animation reset
        // uses a plain indexed loop without allocating an Iterator.
        this.modelParts = model.allParts().toArray(ModelPart[]::new);
    }

    /** The wrapped model. Used as a cache key when reusing a McBoneModel across frames. */
    public Model getModel() {
        return model;
    }

    @Override
    public Map<String, IBoneTarget> getBoneIndex() {
        if (boneIndex == null) {
            buildIndex();
        }
        return boneIndex;
    }

    @Override
    public Iterable<IBoneTarget> getAllBones() {
        if (allBones == null) {
            buildIndex();
        }
        return allBones;
    }

    @Override
    public void resetAllBones() {
        for (int i = 0; i < this.modelParts.length; i++) {
            ((IModelPart) (Object) this.modelParts[i]).viaBedrockUtility$resetToDefaultPose();
        }
    }

    private void buildIndex() {
        boneIndex = new HashMap<>();
        allBones = new ArrayList<>(this.modelParts.length);
        for (int i = 0; i < this.modelParts.length; i++) {
            ModelPart part = this.modelParts[i];
            ModelPartBoneTarget bone = new ModelPartBoneTarget(part);
            allBones.add(bone);
            String name = bone.getName();
            if (name != null && !name.isEmpty()) {
                boneIndex.putIfAbsent(name.toLowerCase(Locale.ROOT), bone);
            }
        }
    }
}
