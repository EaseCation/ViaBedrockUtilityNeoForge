package org.oryxel.viabedrockutility.mixin.impl.render;

import net.minecraft.client.model.geom.ModelPart;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.PartPose;
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
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

@Mixin(ModelPart.class)
public abstract class ModelPartMixin implements IModelPart {
    @Shadow public float x;
    @Shadow public float y;
    @Shadow public float z;

    @Shadow @Final private List<ModelPart.Cube> cubes;
    @Shadow @Final private Map<String, ModelPart> children;

    @Shadow public abstract List<ModelPart> getAllParts();

    @Shadow public abstract PartPose getInitialPose();

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

    @Unique
    private static final float VBU_ABSOLUTE_ARM_X_THRESHOLD = 1.0F;

    @Inject(method = "translateAndRotate", at = @At("HEAD"))
    public void render(PoseStack matrices, CallbackInfo ci) {
        // Offset is needed for rotating too!
        matrices.translate(this.offset.x / 16.0F, this.offset.y / 16.0F, this.offset.z / 16.0F);

        matrices.translate(this.pivot.x / 16.0F, this.pivot.y / 16.0F, this.pivot.z / 16.0F);
        matrices.mulPose(this.vbu$tempQuaternion.rotationZYX(this.rotation.z * MathUtil.DEGREES_TO_RADIANS, this.rotation.y * MathUtil.DEGREES_TO_RADIANS, this.rotation.x * MathUtil.DEGREES_TO_RADIANS));
        matrices.translate(-this.pivot.x / 16.0F, -this.pivot.y / 16.0F, -this.pivot.z / 16.0F);

        matrices.translate(-this.offset.x / 16.0F, -this.offset.y / 16.0F, -this.offset.z / 16.0F);

        // Re-translate to the bone's Bedrock pivot so that vanilla's rotation/scale (applied next inside
        // the original translateAndRotate body, about the part's setPos origin) pivots about the SAME
        // point as our VBU rotation above. With the unified coordinate scheme setPos is always 0, so that
        // origin sits at part-space (0,0,0) = Bedrock y=24.016 ≈ the top of the model; without this,
        // vanilla setupAnim made plain (non-Bedrock-animated) player legs rotate from the head. Undone at
        // TAIL, so net translation stays identity and absolute cube placement is unchanged. For
        // Bedrock-animated bones vanilla rotation is cleared (PlayerAnimationManager.clearVanillaRotation)
        // and for entities/non-VBU parts it is 0, so this only relocates the pivot where it actually matters.
        matrices.translate(this.pivot.x / 16.0F, this.pivot.y / 16.0F, this.pivot.z / 16.0F);
    }

    @Inject(method = "translateAndRotate", at = @At("TAIL"))
    public void renderTail(PoseStack matrices, CallbackInfo ci) {
        // Undo the pivot translate added at HEAD's tail: it wrapped vanilla's rotation/scale so they pivot
        // about the Bedrock pivot. Reverting it here keeps the bone's net translation identity.
        matrices.translate(-this.pivot.x / 16.0F, -this.pivot.y / 16.0F, -this.pivot.z / 16.0F);

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

    @Inject(method = "copyFrom", at = @At("TAIL"))
    private void keepVanillaPartOriginsWhenCopyingFromVBU(ModelPart source, CallbackInfo ci) {
        final IModelPart sourcePart = (IModelPart) (Object) source;
        if (this.isVBUModel || !sourcePart.viaBedrockUtility$isVBUModel()) {
            return;
        }

        // Vanilla armor/cape models need their baked part offsets (legs at y=12, arms at +/-5,y=2),
        // but must still inherit dynamic pose translations from crouching, swimming, etc.
        PartPose targetInitialPose = this.getInitialPose();
        PartPose sourceInitialPose = source.getInitialPose();
        this.x = targetInitialPose.x() + (source.x - sourceInitialPose.x());
        this.y = targetInitialPose.y() + (source.y - sourceInitialPose.y());
        this.z = targetInitialPose.z() + (source.z - sourceInitialPose.z());

        // During the vanilla left-click swing, HumanoidModel writes the arm x/z positions as absolute
        // vanilla arm origins. VBU player arms use a render-time X/Z cancellation for their visual arm
        // geometry, while the vanilla armor model does not, so copy the visible arm's X convention and do
        // not inherit the transient Z offset that would make sleeves drift slightly during the swing.
        if (this.vbu$usesAbsoluteSwingArmPosition(sourcePart, source, sourceInitialPose)) {
            this.x = source.x;
            this.z = targetInitialPose.z();
        }
    }

    @Unique
    private boolean vbu$usesAbsoluteSwingArmPosition(IModelPart sourcePart, ModelPart source, PartPose sourceInitialPose) {
        final String partName = sourcePart.viaBedrockUtility$getName();
        if (partName == null) {
            return false;
        }

        final String normalizedName = partName.replace("_", "").toLowerCase(Locale.ROOT);
        return ("leftarm".equals(normalizedName) || "rightarm".equals(normalizedName))
                && Math.abs(source.x - sourceInitialPose.x()) >= VBU_ABSOLUTE_ARM_X_THRESHOLD;
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
    public Vector3f viaBedrockUtility$getPivot() {
        return this.pivot;
    }

    @Override
    public void viaBedrockUtility$resetToDefaultPose() {
        this.rotation.set(this.defaultRotation);
        this.offset.set(0, 0, 0);
        this.xScale = this.yScale = this.zScale = 1.0F;
    }

    // --- forEachChild depth guard (DISABLED — kept for debugging cyclic ModelPart trees) ---
    // This mixin injects into every ModelPart.forEachChild call (all entities, every frame),
    // so it has non-trivial performance overhead from ThreadLocal access.
    // The root cause (Bedrock skins with "world" → "root" hierarchy) is now fixed in
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
                        "[VBU] ModelPart.forEachChild depth > {} — likely cycle! bone='{}', isVBU={}. Suppressing further warnings.",
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
