package org.oryxel.viabedrockutility.mixin.impl.render;

import net.minecraft.client.model.geom.ModelPart;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.mixin.interfaces.ICuboid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.Cube.class)
public abstract class CuboidMixin implements ICuboid {
    @Shadow @Final public ModelPart.Polygon[] polygons;

    @Unique
    private boolean isVBU = false;

    @Unique
    private float vOffset = 0f;

    @Override
    public boolean viaBedrockUtility$isVBUCuboid() {
        return this.isVBU;
    }

    @Override
    public void viaBedrockUtility$markAsVBU() {
        this.isVBU = true;
    }

    @Override
    public float viaBedrockUtility$getVOffset() {
        return this.vOffset;
    }

    @Override
    public void viaBedrockUtility$setVOffset(float offset) {
        this.vOffset = offset;
    }

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void vbu$renderCuboid(PoseStack.Pose entry, VertexConsumer buffer, int light, int overlay, int color, CallbackInfo ci) {
        if (!this.isVBU) return;
        ci.cancel();

        Matrix4f posMatrix = entry.pose();
        Vector3f tempVec = new Vector3f();

        for (ModelPart.Polygon quad : this.polygons) {
            if (quad == null) continue;

            Vector3f transformedNormal = entry.transformNormal(quad.normal(), tempVec);
            float nx = transformedNormal.x();
            float ny = transformedNormal.y();
            float nz = transformedNormal.z();

            for (ModelPart.Vertex vertex : quad.vertices()) {
                float wx = vertex.pos().x() / 16.0f;
                float wy = vertex.pos().y() / 16.0f;
                float wz = vertex.pos().z() / 16.0f;

                Vector3f pos = posMatrix.transformPosition(wx, wy, wz, tempVec);
                buffer.addVertex(pos.x(), pos.y(), pos.z(), color, vertex.u(), vertex.v() + this.vOffset, overlay, light, nx, ny, nz);
            }
        }
    }
}
