package org.oryxel.viabedrockutility.adapter;

import net.easecation.bedrockmotion.model.IBoneTarget;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapter: wraps MC ModelPart (with IModelPart Mixin) as IBoneTarget.
 * Coordinate transformations (e.g., Y-negation for offset) are handled
 * by the Mixin implementation, so the engine stays in Bedrock coordinate space.
 */
public class ModelPartBoneTarget implements IBoneTarget {
    private final ModelPart part;
    private final IModelPart mixin;
    private Map<String, IBoneTarget> children;

    public ModelPartBoneTarget(ModelPart part) {
        this.part = part;
        this.mixin = (IModelPart) (Object) part;
    }

    @Override
    public String getName() {
        return mixin.viaBedrockUtility$getName();
    }

    @Override
    public Vector3f getRotation() {
        return mixin.viaBedrockUtility$getRotation();
    }

    @Override
    public Vector3f getOffset() {
        return mixin.viaBedrockUtility$getOffset();
    }

    @Override
    public float getScaleX() {
        return part.xScale;
    }

    @Override
    public float getScaleY() {
        return part.yScale;
    }

    @Override
    public float getScaleZ() {
        return part.zScale;
    }

    @Override
    public void setScale(float x, float y, float z) {
        part.xScale = x;
        part.yScale = y;
        part.zScale = z;
    }

    @Override
    public void addOffset(Vector3f offset) {
        // Delegates to Mixin which negates Y for MC coordinate system
        mixin.viaBedrockUtility$addOffset(offset);
    }

    @Override
    public void addRotation(Vector3f rotation) {
        mixin.viaBedrockUtility$addAngles(rotation);
    }

    @Override
    public void addScale(float dx, float dy, float dz) {
        part.xScale += dx;
        part.yScale += dy;
        part.zScale += dz;
    }

    @Override
    public void resetToDefaultPose() {
        mixin.viaBedrockUtility$resetToDefaultPose();
    }

    @Override
    public void resetEverything() {
        mixin.viaBedrockUtility$resetEverything();
    }

    @Override
    public Map<String, IBoneTarget> getChildren() {
        if (children == null) {
            children = new HashMap<>();
            for (Map.Entry<String, ModelPart> entry : mixin.viaBedrockUtility$getChildren().entrySet()) {
                children.put(entry.getKey(), new ModelPartBoneTarget(entry.getValue()));
            }
        }
        return children;
    }
}
