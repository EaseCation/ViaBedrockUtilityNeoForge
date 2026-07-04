package org.oryxel.viabedrockutility.mixin.interfaces;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

import java.util.Map;

public interface IModelPart {
    boolean viaBedrockUtility$isVBUModel();
    void viaBedrockUtility$setName(String name);
    String viaBedrockUtility$getName();
    void viaBedrockUtility$resetEverything();
    void viaBedrockUtility$setVBUModel();
    void viaBedrockUtility$setNeededOffset(boolean needed);
    void viaBedrockUtility$setOffset(Vector3f vec3);
    void viaBedrockUtility$setPivot(Vector3f vec3);
    void viaBedrockUtility$setAngles(Vector3f vec3);
    void viaBedrockUtility$addOffset(Vector3f vec3);
    void viaBedrockUtility$addAngles(Vector3f vec3);
    Vector3f viaBedrockUtility$getRotation();
    Vector3f viaBedrockUtility$getOffset();
    Vector3f viaBedrockUtility$getPivot();
    void viaBedrockUtility$resetToDefaultPose();
    Map<String, ModelPart> viaBedrockUtility$getChildren();
    java.util.List<ModelPart.Cube> viaBedrockUtility$getCuboids();

    /**
     * Invalidate the cached children iteration list built lazily in ModelPartMixin's render redirect.
     * Called after the children map is structurally mutated (only happens at build time in GeometryUtil,
     * e.g. validateAndFixCycles removing a cyclic edge) so the next render rebuilds the array snapshot.
     */
    default void viaBedrockUtility$invalidateChildrenCache() {}
}
