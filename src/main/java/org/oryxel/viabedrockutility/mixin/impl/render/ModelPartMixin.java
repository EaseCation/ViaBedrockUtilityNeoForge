package org.oryxel.viabedrockutility.mixin.impl.render;

import net.minecraft.client.model.geom.ModelPart;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.Direction;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;
import org.oryxel.viabedrockutility.renderer.VbuCompileScratch;
import org.oryxel.viabedrockutility.renderer.VbuCuboidBatchRenderer;
import org.oryxel.viabedrockutility.renderer.VbuRenderMetrics;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

@Mixin(ModelPart.class)
public abstract class ModelPartMixin implements IModelPart {
    @Shadow public float x;
    @Shadow public float y;
    @Shadow public float z;
    @Shadow public float xRot;
    @Shadow public float yRot;
    @Shadow public float zRot;
    @Shadow public boolean visible;
    @Shadow public boolean skipDraw;

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
    @Unique private boolean vbu$cubeGroup;
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

    @Unique private static final ModelPart[] vbu$EMPTY_CHILDREN = new ModelPart[0];
    @Unique private static final ModelPart.Cube[] vbu$EMPTY_CUBES = new ModelPart.Cube[0];
    @Unique private ModelPart[] vbu$childrenArray;
    @Unique private ModelPart.Cube[] vbu$cubesArray;
    @Unique private VbuCuboidBatchRenderer.Batch vbu$compiledBatch;
    @Unique private ModelPart.Cube[] vbu$attachmentCubes;

    @Unique
    private static final ModelPart.Cube vbu$EMPTY_ATTACHMENT_CUBE = new ModelPart.Cube(
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, false, 1, 1, java.util.Set.<Direction>of());

    @Unique
    private static final float VBU_ABSOLUTE_ARM_X_THRESHOLD = 1.0F;
    @Unique
    private static final boolean vbu$INDEXED_RENDER_ENABLED =
            !Boolean.getBoolean("viabedrockutility.disableIndexedRender");

    @Inject(method = "translateAndRotate", at = @At("HEAD"))
    public void render(PoseStack matrices, CallbackInfo ci) {
        if (!this.isVBUModel) {
            return;
        }

        final boolean hasCustomRotation = this.rotation.x != 0.0F
                || this.rotation.y != 0.0F || this.rotation.z != 0.0F;
        if (hasCustomRotation) {
            // Offset participates in the custom rotation, but the final offset is applied at TAIL so it
            // remains unaffected by vanilla scale.
            if (this.vbu$hasOffset()) {
                matrices.translate(this.offset.x / 16.0F, this.offset.y / 16.0F, this.offset.z / 16.0F);
            }
            if (this.vbu$hasPivot()) {
                matrices.translate(this.pivot.x / 16.0F, this.pivot.y / 16.0F, this.pivot.z / 16.0F);
            }
            matrices.mulPose(this.vbu$tempQuaternion.rotationZYX(
                    this.rotation.z * MathUtil.DEGREES_TO_RADIANS,
                    this.rotation.y * MathUtil.DEGREES_TO_RADIANS,
                    this.rotation.x * MathUtil.DEGREES_TO_RADIANS));
            if (this.vbu$hasPivot()) {
                matrices.translate(-this.pivot.x / 16.0F, -this.pivot.y / 16.0F, -this.pivot.z / 16.0F);
            }
            if (this.vbu$hasOffset()) {
                matrices.translate(-this.offset.x / 16.0F, -this.offset.y / 16.0F, -this.offset.z / 16.0F);
            }
        }

        // Re-translate to the bone's Bedrock pivot so that vanilla's rotation/scale (applied next inside
        // the original translateAndRotate body, about the part's setPos origin) pivots about the SAME
        // point as our VBU rotation above. With the unified coordinate scheme setPos is always 0, so that
        // origin sits at part-space (0,0,0) = Bedrock y=24.016 ≈ the top of the model; without this,
        // vanilla setupAnim made plain (non-Bedrock-animated) player legs rotate from the head. Undone at
        // TAIL, so net translation stays identity and absolute cube placement is unchanged. For
        // Bedrock-animated bones vanilla rotation is cleared (PlayerAnimationManager.clearVanillaRotation)
        // and for entities/non-VBU parts it is 0, so this only relocates the pivot where it actually matters.
        if (this.vbu$needsVanillaPivotWrapper()) {
            matrices.translate(this.pivot.x / 16.0F, this.pivot.y / 16.0F, this.pivot.z / 16.0F);
        }
    }

    @Inject(method = "translateAndRotate", at = @At("TAIL"))
    public void renderTail(PoseStack matrices, CallbackInfo ci) {
        if (!this.isVBUModel) {
            return;
        }

        // Undo the pivot translate added at HEAD's tail: it wrapped vanilla's rotation/scale so they pivot
        // about the Bedrock pivot. Reverting it here keeps the bone's net translation identity.
        if (this.vbu$needsVanillaPivotWrapper()) {
            matrices.translate(-this.pivot.x / 16.0F, -this.pivot.y / 16.0F, -this.pivot.z / 16.0F);
        }

        // Do this after scale since well, this shouldn't be affected by scaling.
        if (this.vbu$hasOffset()) {
            matrices.translate(this.offset.x / 16.0F, this.offset.y / 16.0F, this.offset.z / 16.0F);
        }

        if (!this.neededOffset) {
            return;
        }

        // Have to do this because of how java pivot point and bedrock pivot point system works, I think? ehhh whatever it works, just don't touch it.
        if (this.x != 0.0F || this.z != 0.0F) {
            matrices.translate(-this.x / 16.0F, 0, -this.z / 16.0F);
        }
    }

    @Unique
    private boolean vbu$hasPivot() {
        return this.pivot.x != 0.0F || this.pivot.y != 0.0F || this.pivot.z != 0.0F;
    }

    @Unique
    private boolean vbu$hasOffset() {
        return this.offset.x != 0.0F || this.offset.y != 0.0F || this.offset.z != 0.0F;
    }

    @Unique
    private boolean vbu$needsVanillaPivotWrapper() {
        return this.vbu$hasPivot()
                && (this.xRot != 0.0F || this.yRot != 0.0F || this.zRot != 0.0F
                || this.xScale != 1.0F || this.yScale != 1.0F || this.zScale != 1.0F);
    }

    /**
     * Mirrors Minecraft 1.21.8's five-argument ModelPart.render exactly, replacing only List/Map iteration
     * with immutable array snapshots for VBU parts. Child calls remain recursive so rendering a player arm,
     * head, or any other subtree directly keeps vanilla PoseStack and visibility semantics.
     */
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void vbu$interceptIndexedRender(PoseStack matrices, VertexConsumer vertices, int light, int overlay,
                                            int color, CallbackInfo ci) {
        if (!this.isVBUModel || !vbu$INDEXED_RENDER_ENABLED) {
            return;
        }
        ci.cancel();

        VbuRenderMetrics.recordModelRender();
        this.viaBedrockUtility$renderIndexed(matrices, vertices, light, overlay, color);
    }

    @Override
    public void viaBedrockUtility$renderIndexed(PoseStack matrices, VertexConsumer vertices,
                                                 int light, int overlay, int color) {
        this.vbu$ensureTopologyCache();
        // Cube groups are an internal split of their owning bone's geometry. Layers may toggle every
        // ModelPart and then enable only a canonical bone, so group-local visibility must not override
        // the owner's state.
        if ((!this.vbu$cubeGroup && !this.visible)
                || (this.vbu$cubesArray.length == 0 && this.vbu$childrenArray.length == 0)) {
            return;
        }
        VbuRenderMetrics.recordPart();

        matrices.pushPose();
        try {
            ((ModelPart) (Object) this).translateAndRotate(matrices);
            if (this.vbu$cubeGroup || !this.skipDraw) {
                final PoseStack.Pose pose = matrices.last();
                final ModelPart.Cube[] cubes = this.vbu$cubesArray;
                if (cubes.length > 0 && (this.vbu$compiledBatch == null
                        || !VbuCuboidBatchRenderer.tryRender(
                        pose, vertices, this.vbu$compiledBatch,
                        light, overlay, color, VbuCompileScratch.FLAT_NORMAL))) {
                    for (int i = 0; i < cubes.length; i++) {
                        cubes[i].compile(pose, vertices, light, overlay, color);
                    }
                }
            }

            final ModelPart[] children = this.vbu$childrenArray;
            for (int i = 0; i < children.length; i++) {
                final ModelPart child = children[i];
                final IModelPart extension = (IModelPart) (Object) child;
                if (extension.viaBedrockUtility$isVBUModel()) {
                    if (extension.viaBedrockUtility$isCubeGroup() && this.skipDraw) {
                        continue;
                    }
                    extension.viaBedrockUtility$renderIndexed(matrices, vertices, light, overlay, color);
                } else {
                    child.render(matrices, vertices, light, overlay, color);
                }
            }
        } finally {
            matrices.popPose();
        }
    }

    @Unique
    private void vbu$ensureTopologyCache() {
        if (this.vbu$cubesArray == null) {
            this.vbu$cubesArray = this.cubes.isEmpty() ? vbu$EMPTY_CUBES : this.cubes.toArray(vbu$EMPTY_CUBES);
            this.vbu$compiledBatch = VbuCuboidBatchRenderer.compile(this.vbu$cubesArray);
        }
        if (this.vbu$childrenArray == null) {
            this.vbu$childrenArray = this.children.isEmpty() ? vbu$EMPTY_CHILDREN : this.children.values().toArray(vbu$EMPTY_CHILDREN);
        }
        if (this.vbu$attachmentCubes == null) {
            this.vbu$attachmentCubes = this.vbu$buildAttachmentCubes();
        }
    }

    @Unique
    private ModelPart.Cube[] vbu$buildAttachmentCubes() {
        if (this.vbu$childrenArray.length == 0) {
            return vbu$EMPTY_CUBES;
        }

        final List<ModelPart.Cube> attachmentCubes = new ArrayList<>();
        for (ModelPart child : this.vbu$childrenArray) {
            final IModelPart extension = (IModelPart) (Object) child;
            if (!extension.viaBedrockUtility$isCubeGroup()) {
                continue;
            }

            final List<ModelPart.Cube> childCubes = extension.viaBedrockUtility$getCuboids();
            if (childCubes.isEmpty()) {
                continue;
            }
            final Vector3f childRotation = extension.viaBedrockUtility$getRotation();
            final boolean rotated = childRotation.x != 0.0F
                    || childRotation.y != 0.0F || childRotation.z != 0.0F;
            final Vector3f childPivot = extension.viaBedrockUtility$getPivot();
            for (ModelPart.Cube cube : childCubes) {
                // Normal boxes already expose useful bounds. Rotated cubes and poly meshes need a
                // bone-local AABB because StuckInBodyLayer never applies the cube-group transform.
                if (!rotated && (cube.minX != cube.maxX || cube.minY != cube.maxY || cube.minZ != cube.maxZ)) {
                    attachmentCubes.add(cube);
                } else {
                    final ModelPart.Cube bounds = vbu$createAttachmentBounds(
                            cube, childPivot, childRotation, rotated);
                    if (bounds != null) {
                        attachmentCubes.add(bounds);
                    }
                }
            }
        }

        return attachmentCubes.isEmpty()
                ? vbu$EMPTY_CUBES
                : attachmentCubes.toArray(vbu$EMPTY_CUBES);
    }

    @Unique
    private static ModelPart.Cube vbu$createAttachmentBounds(ModelPart.Cube cube,
                                                              Vector3f pivot,
                                                              Vector3f rotation,
                                                              boolean rotated) {
        final Quaternionf quaternion = rotated
                ? new Quaternionf().rotationZYX(
                        rotation.z * MathUtil.DEGREES_TO_RADIANS,
                        rotation.y * MathUtil.DEGREES_TO_RADIANS,
                        rotation.x * MathUtil.DEGREES_TO_RADIANS)
                : null;
        final Vector3f position = new Vector3f();
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (ModelPart.Polygon polygon : cube.polygons) {
            if (polygon == null) {
                continue;
            }
            for (ModelPart.Vertex vertex : polygon.vertices()) {
                position.set(vertex.pos());
                if (quaternion != null) {
                    position.sub(pivot);
                    quaternion.transform(position);
                    position.add(pivot);
                }
                minX = Math.min(minX, position.x);
                minY = Math.min(minY, position.y);
                minZ = Math.min(minZ, position.z);
                maxX = Math.max(maxX, position.x);
                maxY = Math.max(maxY, position.y);
                maxZ = Math.max(maxZ, position.z);
            }
        }

        if (!Float.isFinite(minX) || !Float.isFinite(maxX)) {
            return null;
        }
        return new ModelPart.Cube(
                0, 0, minX, minY, minZ, maxX - minX, maxY - minY, maxZ - minZ,
                0, 0, 0, false, 1, 1, java.util.Set.<Direction>of());
    }

    @Override
    public void viaBedrockUtility$freezeTopology() {
        this.vbu$cubesArray = this.cubes.isEmpty() ? vbu$EMPTY_CUBES : this.cubes.toArray(vbu$EMPTY_CUBES);
        this.vbu$compiledBatch = VbuCuboidBatchRenderer.compile(this.vbu$cubesArray);
        this.vbu$childrenArray = this.children.isEmpty() ? vbu$EMPTY_CHILDREN : this.children.values().toArray(vbu$EMPTY_CHILDREN);
        this.vbu$attachmentCubes = this.vbu$buildAttachmentCubes();
    }

    @Override
    public void viaBedrockUtility$invalidateChildrenCache() {
        this.vbu$cubesArray = null;
        this.vbu$childrenArray = null;
        this.vbu$compiledBatch = null;
        this.vbu$attachmentCubes = null;
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

    @Inject(method = "getRandomCube", at = @At("HEAD"), cancellable = true)
    private void vbu$getAttachmentCube(RandomSource random, CallbackInfoReturnable<ModelPart.Cube> cir) {
        if (!this.isVBUModel || !this.cubes.isEmpty()) {
            return;
        }

        this.vbu$ensureTopologyCache();
        final ModelPart.Cube[] attachmentCubes = this.vbu$attachmentCubes;
        cir.setReturnValue(attachmentCubes.length > 0
                ? attachmentCubes[random.nextInt(attachmentCubes.length)]
                : vbu$EMPTY_ATTACHMENT_CUBE);
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
        // vanilla arm origins. VBU player arms later cancel that dynamic X/Z offset after vanilla's ZYX
        // rotation, so the armor origin has to keep only the residual that remains after the same rotation
        // instead of inheriting the full offset (front drift) or dropping all of it (back drift).
        if (this.vbu$usesAbsoluteSwingArmPosition(sourcePart, source, sourceInitialPose)) {
            this.vbu$copyAbsoluteSwingArmPosition(source, targetInitialPose);
        }
    }

    @Unique
    private void vbu$copyAbsoluteSwingArmPosition(ModelPart source, PartPose targetInitialPose) {
        final float swingOffsetX = source.x - targetInitialPose.x();
        final float swingOffsetZ = source.z - targetInitialPose.z();
        final Vector3f rotatedSwingOffset = new Vector3f(swingOffsetX, 0.0F, swingOffsetZ)
                .rotate(this.vbu$tempQuaternion.rotationZYX(source.zRot, source.yRot, source.xRot));
        this.x = targetInitialPose.x() + swingOffsetX - rotatedSwingOffset.x;
        this.z = targetInitialPose.z() + swingOffsetZ - rotatedSwingOffset.z;
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
    public boolean viaBedrockUtility$isNeededOffset() {
        return this.neededOffset;
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
    public void viaBedrockUtility$setCubeGroup() {
        this.vbu$cubeGroup = true;
    }

    @Override
    public boolean viaBedrockUtility$isCubeGroup() {
        return this.vbu$cubeGroup;
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

    /**
     * Stores the offset as-is: the Bedrock->Java Y negation now happens once at the adapter
     * boundary (ModelPartBoneTarget.addOffset), so this mixin only ever sees Java-space values.
     */
    @Override
    public void viaBedrockUtility$setOffset(Vector3f vec3) {
        this.offset.set(vec3);
    }

    @Override
    public void viaBedrockUtility$addOffset(Vector3f vec3) {
        this.offset.add(vec3);
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
