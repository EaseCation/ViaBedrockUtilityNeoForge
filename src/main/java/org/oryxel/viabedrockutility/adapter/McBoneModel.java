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
    private Map<String, IBoneTarget> boneIndex;
    private List<IBoneTarget> allBones;

    public McBoneModel(Model model) {
        this.model = model;
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
        @SuppressWarnings("unchecked")
        List<ModelPart> parts = (List<ModelPart>) model.allParts();
        for (ModelPart part : parts) {
            ((IModelPart) (Object) part).viaBedrockUtility$resetToDefaultPose();
        }
    }

    private void buildIndex() {
        boneIndex = new HashMap<>();
        allBones = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<ModelPart> parts = (List<ModelPart>) model.allParts();
        for (ModelPart part : parts) {
            ModelPartBoneTarget bone = new ModelPartBoneTarget(part);
            allBones.add(bone);
            String name = bone.getName();
            if (name != null && !name.isEmpty()) {
                boneIndex.putIfAbsent(name.toLowerCase(Locale.ROOT), bone);
            }
        }
    }
}
