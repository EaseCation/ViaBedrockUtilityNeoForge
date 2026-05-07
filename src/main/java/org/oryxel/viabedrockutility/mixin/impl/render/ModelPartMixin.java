package org.oryxel.viabedrockutility.mixin.impl.render;

import net.minecraft.client.model.geom.ModelPart;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;
import net.easecation.bedrockmotion.util.MathUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

@Mixin(ModelPart.class)
public abstract class ModelPartMixin implements IModelPart {
    @Shadow public float x;
    @Shadow public float z;

    @Shadow @Final private List<ModelPart.Cube> cubes;
    @Shadow @Final private Map<String, ModelPart> children;

    @Shadow public abstract List<ModelPart> getAllParts();

    @Shadow public float xScale;
    @Shadow public float yScale;
    @Shadow public float zScale;
    @Unique
    private String name = "";

    @Unique private boolean isVBUModel;
    @Unique private boolean neededOffset;

    @Unique
    private final Quaternionf vbu$tempQuaternion = new Quaternionf();

    @Unique
    private Vector3f pivot = new Vector3f();

    @Unique
    private Vector3f offset = new Vector3f();

    @Unique
    private Vector3f rotation = new Vector3f();
    
    @Unique
    private Vector3f defaultRotation = new Vector3f();

    @Unique
    private boolean alreadySetRotation = false;

    @Inject(method = "translateAndRotate", at = @At("HEAD"))
    public void render(PoseStack matrices, CallbackInfo ci) {
        // Offset is needed for rotating too!
        matrices.translate(this.offset.x / 16.0F, this.offset.y / 16.0F, this.offset.z / 16.0F);

        matrices.translate(this.pivot.x / 16.0F, this.pivot.y / 16.0F, this.pivot.z / 16.0F);
        matrices.mulPose(this.vbu$tempQuaternion.rotationZYX(this.rotation.z * MathUtil.DEGREES_TO_RADIANS, this.rotation.y * MathUtil.DEGREES_TO_RADIANS, this.rotation.x * MathUtil.DEGREES_TO_RADIANS));
        matrices.translate(-this.pivot.x / 16.0F, -this.pivot.y / 16.0F, -this.pivot.z / 16.0F);

        matrices.translate(-this.offset.x / 16.0F, -this.offset.y / 16.0F, -this.offset.z / 16.0F);
    }

    @Inject(method = "translateAndRotate", at = @At("TAIL"))
    public void renderTail(PoseStack matrices, CallbackInfo ci) {
        // Do this after scale since well, this shouldn't be affected by scaling.
        matrices.translate(this.offset.x / 16.0F, this.offset.y / 16.0F, this.offset.z / 16.0F);

        if (!this.isVBUModel || !this.neededOffset) {
            return;
        }

        // Have to do this because of how java pivot point and bedrock pivot point system works, I think? ehhh whatever it works, just don't touch it.
        matrices.translate(-this.x / 16.0F, 0, -this.z / 16.0F);
    }

    @Inject(method = "getChild", at = @At("HEAD"), cancellable = true)
    private void getChild(String name, CallbackInfoReturnable<ModelPart> cir) {
        if (this.isVBUModel) {
            ModelPart child = this.children.get(name);
            if (child == null) {
                child = new ModelPart(List.of(), Map.of());
                ((IModelPart)(Object) child).viaBedrockUtility$setVBUModel();
            }
            cir.setReturnValue(child);
        }
    }

    @Override
    public String viaBedrockUtility$getName() {
        return this.name;
    }

    @Override
    public void viaBedrockUtility$setName(String name) {
        this.name = name;
    }

    @Override
    public void viaBedrockUtility$setNeededOffset(boolean needed) {
        this.neededOffset = needed;
    }

    @Override
    public boolean viaBedrockUtility$isVBUModel() {
        return this.isVBUModel;
    }

    @Override
    public void viaBedrockUtility$setVBUModel() {
        this.isVBUModel = true;
    }

    @Override
    public void viaBedrockUtility$resetEverything() {
        this.getAllParts().forEach(part -> {
            ((IModelPart)((Object)part)).viaBedrockUtility$resetToDefaultPose();
        });
    }

    @Override
    public void viaBedrockUtility$setPivot(Vector3f vec3) {
        this.pivot = vec3;
    }

    @Override
    public void viaBedrockUtility$setOffset(Vector3f vec3) {
        this.offset.set(vec3.x, -vec3.y, vec3.z);
    }

    @Override
    public void viaBedrockUtility$addOffset(Vector3f vec3) {
        this.offset.add(vec3.x, -vec3.y, vec3.z);
    }

    @Override
    public Map<String, ModelPart> viaBedrockUtility$getChildren() {
        return this.children;
    }

    @Override
    public List<ModelPart.Cube> viaBedrockUtility$getCuboids() {
        return this.cubes;
    }

    @Override
    public void viaBedrockUtility$setAngles(Vector3f vec3) {
        if (!this.alreadySetRotation) {
            this.defaultRotation.set(vec3.x, vec3.y, vec3.z);
            this.alreadySetRotation = true;
        }

        this.rotation.set(vec3.x, vec3.y, vec3.z);
    }

    @Override
    public void viaBedrockUtility$addAngles(Vector3f vec3) {
        this.rotation.add(vec3.x, vec3.y, vec3.z);
    }

    @Override
    public Vector3f viaBedrockUtility$getRotation() {
        return this.rotation;
    }

    @Override
    public Vector3f viaBedrockUtility$getOffset() {
        return this.offset;
    }

    @Override
    public void viaBedrockUtility$resetToDefaultPose() {
        this.rotation.set(this.defaultRotation);
        this.offset.set(0, 0, 0);
        this.xScale = this.yScale = this.zScale = 1.0F;
    }

    // --- forEachChild depth guard (DISABLED, kept for debugging cyclic ModelPart trees) ---
    // This mixin injects into every ModelPart.forEachChild call (all entities, every frame),
    // so it has non-trivial performance overhead from ThreadLocal access.
            // (e.g. Bedrock skins with "world" -> "root" hierarchy where "root" is already the tree root)
    // GeometryUtil.buildModel() via the root.part() identity check + validateAndFixCycles safety net.
    //
    // To re-enable: uncomment the @Inject annotations below, and add this class back to
    // viabedrockutility.mixins.json under "client" as "render.ModelPartForEachChildMixin"
    // (or keep it in ModelPartMixin if it's still registered there).

    /*
    @Unique
    private static final ThreadLocal<Integer> vbu$forEachChildDepth = ThreadLocal.withInitial(() -> 0);

    @Unique
    private static final int VBU_MAX_FOREACHECHILD_DEPTH = 200;

    @Unique
    private static volatile boolean vbu$depthWarningLogged = false;

    @Inject(method = "forEachChild", at = @At("HEAD"), cancellable = true)
    private void vbu$onForEachChildHead(BiConsumer<String, ModelPart> consumer, CallbackInfo ci) {
        int depth = vbu$forEachChildDepth.get();
        if (depth > VBU_MAX_FOREACHECHILD_DEPTH) {
            if (!vbu$depthWarningLogged) {
                vbu$depthWarningLogged = true;
                ViaBedrockUtilityNeoForge.LOGGER.error(
                        "[VBU] ModelPart.forEachChild depth > {} - likely cycle! bone='{}', isVBU={}. Suppressing further warnings.",
                        VBU_MAX_FOREACHECHILD_DEPTH, this.name, this.isVBUModel);
            }
            ci.cancel();
            return;
        }
        vbu$forEachChildDepth.set(depth + 1);
    }

    @Inject(method = "forEachChild", at = @At("RETURN"))
    private void vbu$onForEachChildReturn(BiConsumer<String, ModelPart> consumer, CallbackInfo ci) {
        int depth = vbu$forEachChildDepth.get();
        if (depth > 0) {
            vbu$forEachChildDepth.set(depth - 1);
        }
    }
    */
}
