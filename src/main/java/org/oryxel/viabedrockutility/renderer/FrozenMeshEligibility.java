package org.oryxel.viabedrockutility.renderer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.oryxel.viabedrockutility.mixin.interfaces.ICuboid;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;
import org.oryxel.viabedrockutility.renderer.model.CustomEntityModel;

import java.lang.reflect.Method;

public final class FrozenMeshEligibility {
    private static final IrisState IRIS = IrisState.create();

    private FrozenMeshEligibility() {
    }

    public static boolean isEligible(RenderType renderType, ResourceLocation texture,
                                     MultiBufferSource bufferSource, CustomEntityModel<?> model) {
        return isRenderContextEligible(renderType, texture, bufferSource)
                && !hasDynamicUv(model);
    }

    static boolean isRenderContextEligible(RenderType renderType, ResourceLocation texture,
                                           MultiBufferSource bufferSource) {
        return isStandardRenderType(renderType, texture)
                && !renderType.isOutline()
                && bufferSource instanceof MultiBufferSource.BufferSource
                && !IRIS.shaderPackActive();
    }

    static boolean isStandardRenderType(RenderType renderType, ResourceLocation texture) {
        return renderType == RenderType.entitySolid(texture)
                || renderType == RenderType.entityCutout(texture)
                || renderType == RenderType.entityCutoutNoCull(texture);
    }

    static boolean hasDynamicUv(CustomEntityModel<?> model) {
        for (ModelPart part : model.allParts()) {
            for (ModelPart.Cube cube : ((IModelPart) (Object) part).viaBedrockUtility$getCuboids()) {
                if (((ICuboid) (Object) cube).viaBedrockUtility$getVOffset() != 0.0F) {
                    return true;
                }
            }
        }
        return false;
    }

    private record IrisState(Object api, Method isShaderPackInUse, boolean failClosed) {
        static IrisState create() {
            try {
                Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi", false,
                        FrozenMeshEligibility.class.getClassLoader());
                Method getInstance = apiClass.getMethod("getInstance");
                Object api = getInstance.invoke(null);
                return new IrisState(api, apiClass.getMethod("isShaderPackInUse"), false);
            } catch (ClassNotFoundException absent) {
                return new IrisState(null, null, false);
            } catch (Throwable incompatible) {
                return new IrisState(null, null, true);
            }
        }

        boolean shaderPackActive() {
            if (failClosed) {
                return true;
            }
            if (api == null) {
                return false;
            }
            try {
                return Boolean.TRUE.equals(isShaderPackInUse.invoke(api));
            } catch (Throwable ignored) {
                return true;
            }
        }
    }
}
