package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.easecation.bedrockmotion.util.MathUtil;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.attachable.BedrockTransformConvention;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;

/** Reconstructs the exact matrix produced by VBU's ModelPart render injections. */
public final class BedrockModelPartTransform {
    private BedrockModelPartTransform() {
    }

    public static Matrix4f current(ModelPart part) {
        final IModelPart extension = (IModelPart) (Object) part;
        return compose(extension.viaBedrockUtility$getPivot(), extension.viaBedrockUtility$getOffset(),
                extension.viaBedrockUtility$getRotation(),
                part.x, part.y, part.z, part.xRot, part.yRot, part.zRot,
                part.xScale, part.yScale, part.zScale,
                extension.viaBedrockUtility$isNeededOffset());
    }

    /** Applies the pre-vanilla TRS segment without allocating on the ModelPart render hot path. */
    public static void applyBeforeVanilla(PoseStack poses, ModelPart part, Quaternionf scratchRotation) {
        final IModelPart extension = (IModelPart) (Object) part;
        final Vector3f pivot = extension.viaBedrockUtility$getPivot();
        final Vector3f offset = extension.viaBedrockUtility$getOffset();
        final Vector3f rotation = extension.viaBedrockUtility$getRotation();
        final float inversePixels = 1.0F / BedrockTransformConvention.PIXELS_PER_BLOCK;
        if (nonZero(offset)) {
            poses.translate(offset.x * inversePixels, offset.y * inversePixels, offset.z * inversePixels);
        }
        if (nonZero(rotation)) {
            if (nonZero(pivot)) {
                poses.translate(pivot.x * inversePixels, pivot.y * inversePixels, pivot.z * inversePixels);
            }
            poses.mulPose(scratchRotation.rotationZYX(
                    rotation.z * MathUtil.DEGREES_TO_RADIANS,
                    rotation.y * MathUtil.DEGREES_TO_RADIANS,
                    rotation.x * MathUtil.DEGREES_TO_RADIANS));
            if (nonZero(pivot)) {
                poses.translate(-pivot.x * inversePixels, -pivot.y * inversePixels, -pivot.z * inversePixels);
            }
        }
        if (needsVanillaPivotWrapper(
                pivot, part.xRot, part.yRot, part.zRot, part.xScale, part.yScale, part.zScale)) {
            poses.translate(pivot.x * inversePixels, pivot.y * inversePixels, pivot.z * inversePixels);
        }
    }

    /** Applies the post-vanilla pivot/correction segment without allocating. */
    public static void applyAfterVanilla(PoseStack poses, ModelPart part) {
        final IModelPart extension = (IModelPart) (Object) part;
        final Vector3f pivot = extension.viaBedrockUtility$getPivot();
        final float inversePixels = 1.0F / BedrockTransformConvention.PIXELS_PER_BLOCK;
        if (needsVanillaPivotWrapper(
                pivot, part.xRot, part.yRot, part.zRot, part.xScale, part.yScale, part.zScale)) {
            poses.translate(-pivot.x * inversePixels, -pivot.y * inversePixels, -pivot.z * inversePixels);
        }
        if (extension.viaBedrockUtility$isNeededOffset() && (part.x != 0.0F || part.z != 0.0F)) {
            poses.translate(-part.x * inversePixels, 0.0F, -part.z * inversePixels);
        }
    }

    static Matrix4f compose(Vector3f pivot, Vector3f offset, Vector3f rotation,
                            float x, float y, float z, float xRot, float yRot, float zRot,
                            float xScale, float yScale, float zScale, boolean neededOffset) {
        final boolean vanillaPivotWrapper = needsVanillaPivotWrapper(
                pivot, xRot, yRot, zRot, xScale, yScale, zScale);
        return beforeVanilla(pivot, offset, rotation, vanillaPivotWrapper)
                .translate(x / BedrockTransformConvention.PIXELS_PER_BLOCK,
                        y / BedrockTransformConvention.PIXELS_PER_BLOCK,
                        z / BedrockTransformConvention.PIXELS_PER_BLOCK)
                .rotateZYX(zRot, yRot, xRot)
                .scale(xScale, yScale, zScale)
                .mul(afterVanilla(pivot, vanillaPivotWrapper, neededOffset, x, z));
    }

    private static Matrix4f beforeVanilla(Vector3f pivot, Vector3f offset, Vector3f rotation,
                                          boolean vanillaPivotWrapper) {
        final float inversePixels = 1.0F / BedrockTransformConvention.PIXELS_PER_BLOCK;
        final Matrix4f matrix = new Matrix4f();

        final boolean hasOffset = nonZero(offset);
        final boolean hasPivot = nonZero(pivot);
        final boolean hasCustomRotation = nonZero(rotation);
        if (hasOffset) {
            matrix.translate(offset.x * inversePixels, offset.y * inversePixels, offset.z * inversePixels);
        }
        if (hasCustomRotation) {
            if (hasPivot) {
                matrix.translate(pivot.x * inversePixels, pivot.y * inversePixels, pivot.z * inversePixels);
            }
            matrix.rotateZYX(rotation.z * MathUtil.DEGREES_TO_RADIANS,
                    rotation.y * MathUtil.DEGREES_TO_RADIANS,
                    rotation.x * MathUtil.DEGREES_TO_RADIANS);
            if (hasPivot) {
                matrix.translate(-pivot.x * inversePixels, -pivot.y * inversePixels, -pivot.z * inversePixels);
            }
        }

        if (vanillaPivotWrapper) {
            matrix.translate(pivot.x * inversePixels, pivot.y * inversePixels, pivot.z * inversePixels);
        }
        return matrix;
    }

    private static Matrix4f afterVanilla(Vector3f pivot, boolean vanillaPivotWrapper,
                                         boolean neededOffset, float x, float z) {
        final float inversePixels = 1.0F / BedrockTransformConvention.PIXELS_PER_BLOCK;
        final Matrix4f matrix = new Matrix4f();
        if (vanillaPivotWrapper) {
            matrix.translate(-pivot.x * inversePixels, -pivot.y * inversePixels, -pivot.z * inversePixels);
        }
        if (neededOffset && (x != 0.0F || z != 0.0F)) {
            matrix.translate(-x * inversePixels, 0.0F, -z * inversePixels);
        }
        return matrix;
    }

    private static boolean needsVanillaPivotWrapper(Vector3f pivot,
                                                     float xRot, float yRot, float zRot,
                                                     float xScale, float yScale, float zScale) {
        return nonZero(pivot)
                && (xRot != 0.0F || yRot != 0.0F || zRot != 0.0F
                || xScale != 1.0F || yScale != 1.0F || zScale != 1.0F);
    }

    private static boolean nonZero(Vector3f vector) {
        return vector.x != 0.0F || vector.y != 0.0F || vector.z != 0.0F;
    }
}
