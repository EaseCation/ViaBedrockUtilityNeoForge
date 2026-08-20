package org.oryxel.viabedrockutility.mixin.interfaces;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

import java.util.Map;

public interface IModelPart {
    boolean viaBedrockUtility$isVBUModel();
    void viaBedrockUtility$setName(String name);
    String viaBedrockUtility$getName();
    void viaBedrockUtility$resetEverything();
    void viaBedrockUtility$setVBUModel();
    void viaBedrockUtility$setCubeGroup();
    boolean viaBedrockUtility$isCubeGroup();
    void viaBedrockUtility$setNeededOffset(boolean needed);
    boolean viaBedrockUtility$isNeededOffset();
    /** Stores an already converted Java-space offset; Bedrock->Java Y negation happens at the adapter boundary. */
    void viaBedrockUtility$setOffset(Vector3f vec3);
    void viaBedrockUtility$setPivot(Vector3f vec3);
    void viaBedrockUtility$setAngles(Vector3f vec3);
    /** Adds an already converted Java-space offset; Bedrock->Java Y negation happens at the adapter boundary. */
    void viaBedrockUtility$addOffset(Vector3f vec3);
    void viaBedrockUtility$addAngles(Vector3f vec3);
    Vector3f viaBedrockUtility$getRotation();
    Vector3f viaBedrockUtility$getOffset();
    Vector3f viaBedrockUtility$getPivot();
    void viaBedrockUtility$resetToDefaultPose();
    Map<String, ModelPart> viaBedrockUtility$getChildren();
    java.util.List<ModelPart.Cube> viaBedrockUtility$getCuboids();
    void viaBedrockUtility$renderIndexed(PoseStack matrices, VertexConsumer vertices,
                                         int light, int overlay, int color);

    /**
     * Freeze the cuboid and child snapshots used by the VBU indexed render path. GeometryUtil calls this
     * after the complete ModelPart tree has been linked and cycle-checked.
     */
    default void viaBedrockUtility$freezeTopology() {}

    /**
     * Invalidate the render topology snapshots after a structural mutation. VBU model topology is normally
     * immutable after build; this remains available so build-time repairs cannot leave stale arrays behind.
     */
    default void viaBedrockUtility$invalidateChildrenCache() {}
}
