package org.oryxel.viabedrockutility.renderer;

import net.minecraft.client.model.geom.ModelPart;
import net.easecation.bedrockmotion.util.MathUtil;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.attachable.BedrockTransformConvention;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;

/** Reconstructs the exact matrix produced by VBU's ModelPart render injections. */
public final class BedrockModelPartTransform {
    private BedrockModelPartTransform() {
    }

    public static Matrix4f current(ModelPart part) {
        final IModelPart extension = (IModelPart) (Object) part;
        final Vector3f pivot = extension.viaBedrockUtility$getPivot();
        final Vector3f offset = extension.viaBedrockUtility$getOffset();
        final Vector3f rotation = extension.viaBedrockUtility$getRotation();
        final float inversePixels = 1.0F / BedrockTransformConvention.PIXELS_PER_BLOCK;
        final Matrix4f matrix = new Matrix4f();

        final boolean hasOffset = nonZero(offset);
        final boolean hasPivot = nonZero(pivot);
        final boolean hasCustomRotation = nonZero(rotation);
        if (hasCustomRotation) {
            if (hasOffset) {
                matrix.translate(offset.x * inversePixels, offset.y * inversePixels, offset.z * inversePixels);
            }
            if (hasPivot) {
                matrix.translate(pivot.x * inversePixels, pivot.y * inversePixels, pivot.z * inversePixels);
            }
            matrix.rotateZYX(rotation.z * MathUtil.DEGREES_TO_RADIANS,
                    rotation.y * MathUtil.DEGREES_TO_RADIANS,
                    rotation.x * MathUtil.DEGREES_TO_RADIANS);
            if (hasPivot) {
                matrix.translate(-pivot.x * inversePixels, -pivot.y * inversePixels, -pivot.z * inversePixels);
            }
            if (hasOffset) {
                matrix.translate(-offset.x * inversePixels, -offset.y * inversePixels, -offset.z * inversePixels);
            }
        }

        final boolean vanillaPivotWrapper = hasPivot
                && (part.xRot != 0.0F || part.yRot != 0.0F || part.zRot != 0.0F
                || part.xScale != 1.0F || part.yScale != 1.0F || part.zScale != 1.0F);
        if (vanillaPivotWrapper) {
            matrix.translate(pivot.x * inversePixels, pivot.y * inversePixels, pivot.z * inversePixels);
        }

        matrix.translate(part.x * inversePixels, part.y * inversePixels, part.z * inversePixels);
        if (part.xRot != 0.0F || part.yRot != 0.0F || part.zRot != 0.0F) {
            matrix.rotateZYX(part.zRot, part.yRot, part.xRot);
        }
        if (part.xScale != 1.0F || part.yScale != 1.0F || part.zScale != 1.0F) {
            matrix.scale(part.xScale, part.yScale, part.zScale);
        }

        if (vanillaPivotWrapper) {
            matrix.translate(-pivot.x * inversePixels, -pivot.y * inversePixels, -pivot.z * inversePixels);
        }
        if (hasOffset) {
            matrix.translate(offset.x * inversePixels, offset.y * inversePixels, offset.z * inversePixels);
        }
        if (extension.viaBedrockUtility$isNeededOffset() && (part.x != 0.0F || part.z != 0.0F)) {
            matrix.translate(-part.x * inversePixels, 0.0F, -part.z * inversePixels);
        }
        return matrix;
    }

    private static boolean nonZero(Vector3f vector) {
        return vector.x != 0.0F || vector.y != 0.0F || vector.z != 0.0F;
    }
}
