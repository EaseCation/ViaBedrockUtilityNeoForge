package org.oryxel.viabedrockutility.mixin.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.ARGB;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.mixin.interfaces.ICuboid;
import org.oryxel.viabedrockutility.renderer.SodiumPushBackend;
import org.oryxel.viabedrockutility.renderer.VbuCompileScratch;
import org.oryxel.viabedrockutility.renderer.VbuCompiledCuboid;
import org.oryxel.viabedrockutility.renderer.VbuRenderMetrics;
import org.oryxel.viabedrockutility.renderer.VbuVanillaCuboidRenderer;
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
    private boolean isVBU;

    @Unique
    private float vOffset;

    @Unique
    private VbuCompiledCuboid vbu$compiledGeometry;

    @Unique
    private float[] vbu$boxCoordinates;

    @Unique
    private final Vector3f vbu$temporaryVector = new Vector3f();

    @Override
    public boolean viaBedrockUtility$isVBUCuboid() {
        return this.isVBU;
    }

    @Override
    public void viaBedrockUtility$markAsVBU() {
        this.isVBU = true;
        this.vbu$boxCoordinates = null;
        this.viaBedrockUtility$rebuildCompiledGeometry();
    }

    @Override
    public void viaBedrockUtility$markAsVBUBox(float x0, float y0, float z0,
                                               float x1, float y1, float z1) {
        this.isVBU = true;
        this.vbu$boxCoordinates = new float[]{x0, y0, z0, x1, y1, z1};
        this.viaBedrockUtility$rebuildCompiledGeometry();
    }

    @Override
    public void viaBedrockUtility$rebuildCompiledGeometry() {
        final float[] box = this.vbu$boxCoordinates;
        this.vbu$compiledGeometry = box != null
                ? VbuCompiledCuboid.compileBox(
                        this.polygons, box[0], box[1], box[2], box[3], box[4], box[5])
                : VbuCompiledCuboid.compile(this.polygons);
    }

    @Override
    public VbuCompiledCuboid viaBedrockUtility$getCompiledGeometry() {
        return this.vbu$compiledGeometry;
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
    private void vbu$renderCuboid(PoseStack.Pose pose,
                                  VertexConsumer consumer,
                                  int light,
                                  int overlay,
                                  int color,
                                  CallbackInfo ci) {
        if (!this.isVBU) {
            return;
        }
        ci.cancel();

        VbuCompiledCuboid geometry = this.vbu$compiledGeometry;
        if (geometry == null) {
            this.viaBedrockUtility$rebuildCompiledGeometry();
            geometry = this.vbu$compiledGeometry;
        }

        final Object writer = VbuCompileScratch.tryWriter(consumer);
        if (writer != null && VbuCompileScratch.tryBeginPush()) {
            try {
                final int vertexCount = geometry.vertexCount();
                if (vertexCount == 0) {
                    VbuRenderMetrics.recordEmptyCuboid();
                    return;
                }

                final int stride = SodiumPushBackend.stride();
                final long vertexBuffer = VbuCompileScratch.acquirePushBuffer(vertexCount, stride);
                final int emitted = geometry.writeVertices(
                        pose,
                        vertexBuffer,
                        stride,
                        ARGB.toABGR(color),
                        overlay,
                        light,
                        this.vOffset,
                        VbuCompileScratch.FLAT_NORMAL);
                if (emitted > 0) {
                    SodiumPushBackend.push(writer, vertexBuffer, emitted);
                    VbuRenderMetrics.recordSinglePush(geometry);
                }
                return;
            } finally {
                VbuCompileScratch.endPush();
            }
        }

        VbuRenderMetrics.recordFallback(geometry, writer != null);
        VbuVanillaCuboidRenderer.render(
                this.polygons, pose, consumer, light, overlay, color, this.vOffset,
                VbuCompileScratch.FLAT_NORMAL, this.vbu$temporaryVector);
    }
}
