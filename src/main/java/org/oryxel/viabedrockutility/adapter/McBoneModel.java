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

    /** Includes vanilla channels and inherited-frame metadata, not only Bedrock animation TRS. */
    @Override public Runnable capturePose() {
        float[] values = new float[modelParts.length * 18];
        boolean[] flags = new boolean[modelParts.length * 4];
        org.joml.Matrix4f[] parents = new org.joml.Matrix4f[modelParts.length * 2];
        for (int i = 0; i < modelParts.length; i++) {
            ModelPart part = modelParts[i]; IModelPart extra = (IModelPart)(Object)part;
            int n = i * 18, f = i * 4;
            values[n]=part.x; values[n+1]=part.y; values[n+2]=part.z;
            values[n+3]=part.xRot; values[n+4]=part.yRot; values[n+5]=part.zRot;
            values[n+6]=part.xScale; values[n+7]=part.yScale; values[n+8]=part.zScale;
            var rotation=extra.viaBedrockUtility$getRotation(); var offset=extra.viaBedrockUtility$getOffset(); var pivot=extra.viaBedrockUtility$getPivot();
            values[n+9]=rotation.x; values[n+10]=rotation.y; values[n+11]=rotation.z;
            values[n+12]=offset.x; values[n+13]=offset.y; values[n+14]=offset.z;
            values[n+15]=pivot.x; values[n+16]=pivot.y; values[n+17]=pivot.z;
            flags[f]=extra.viaBedrockUtility$isEntityRelativeRotation(); flags[f+1]=extra.viaBedrockUtility$isNeededOffset(); flags[f+2]=part.visible; flags[f+3]=part.skipDraw;
            var parent=extra.viaBedrockUtility$getParentPose(); var absolute=extra.viaBedrockUtility$getAbsoluteParentPose();
            parents[i*2]=parent==null?null:new org.joml.Matrix4f(parent);
            parents[i*2+1]=absolute==null?null:new org.joml.Matrix4f(absolute);
        }
        return () -> {
            for (int i = 0; i < modelParts.length; i++) {
                ModelPart part=modelParts[i]; IModelPart extra=(IModelPart)(Object)part; int n=i*18,f=i*4;
                part.x=values[n]; part.y=values[n+1]; part.z=values[n+2];
                part.xRot=values[n+3]; part.yRot=values[n+4]; part.zRot=values[n+5];
                part.xScale=values[n+6]; part.yScale=values[n+7]; part.zScale=values[n+8];
                extra.viaBedrockUtility$getRotation().set(values[n+9],values[n+10],values[n+11]);
                extra.viaBedrockUtility$getOffset().set(values[n+12],values[n+13],values[n+14]);
                extra.viaBedrockUtility$getPivot().set(values[n+15],values[n+16],values[n+17]);
                extra.viaBedrockUtility$setEntityRelativeRotation(flags[f]); extra.viaBedrockUtility$setNeededOffset(flags[f+1]); part.visible=flags[f+2]; part.skipDraw=flags[f+3];
                extra.viaBedrockUtility$setParentPose(parents[i*2]); extra.viaBedrockUtility$setAbsoluteParentPose(parents[i*2+1]);
            }
        };
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
