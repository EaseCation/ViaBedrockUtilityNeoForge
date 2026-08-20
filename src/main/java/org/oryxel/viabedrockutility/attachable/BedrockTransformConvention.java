package org.oryxel.viabedrockutility.attachable;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Single source of truth for Bedrock pixel/model space to VBU render space conversion.
 * Legitimate entry points:
 * <ul>
 * <li>{@link #toJavaModel(Vector3f)} — absolute Bedrock pixel position (bone pivot, cube corner,
 * poly_mesh vertex) to Java model space.</li>
 * <li>{@link #toJavaNormal(Vector3f)} — Bedrock direction/normal to Java model space (Y negated,
 * no presentation-origin shift).</li>
 * <li>{@link #bedrockPixelsToRenderSpace()} and {@link #bedrockSceneToRenderSpace(Matrix4f)} —
 * scene-level matrix bridges.</li>
 * <li>{@link #blockbenchSceneToRenderSpace(Matrix4f)}, {@link #blockbenchVectorToJavaModel(Vector3f)}
 * and {@link #blockbenchRotationToJavaModel(Vector3f)} — Blockbench editor space bridges.</li>
 * <li>{@link #bedrockBindingOffset(Vector3f, BindingOffsetFrame)} — Bedrock bone/locator binding
 * offsets to the local axes declared by an anchor.</li>
 * <li>{@link #deformation(Vector3f, Vector3f, Vector3f, Vector3f)}, {@link #hostDeformation(Matrix4f, Matrix4f)}
 * and {@link #hostAttachment(Matrix4f, Matrix4f, Vector3f)} — bone-space composition on already
 * converted values.</li>
 * </ul>
 * Animation offsets cross the Bedrock→Java boundary exactly once, in
 * {@code ModelPartBoneTarget.addOffset} (Y negation); {@code IModelPart} offset/pivot setters store
 * already converted Java-space values.
 */
public final class BedrockTransformConvention {
    public static final float PIXELS_PER_BLOCK = 16.0F;
    public static final float PLAYER_PRESENTATION_ORIGIN_Y = 24.016F;

    private BedrockTransformConvention() {
    }

    /** Local-axis contract of the matrix that receives a Bedrock bind-entity offset. */
    public enum BindingOffsetFrame {
        /** Matrix captured from VBU's already converted Java ModelPart hierarchy. */
        JAVA_MODEL,
        /** Matrix built in the Bedrock owner/head attachment convention or Blockbench preview. */
        OWNER_ATTACHMENT
    }

    public static Vector3f toJavaModel(Vector3f bedrockPixels) {
        return new Vector3f(bedrockPixels.x, -bedrockPixels.y + PLAYER_PRESENTATION_ORIGIN_Y, bedrockPixels.z);
    }

    /** Converts a Bedrock-space direction/normal (Y negated, no presentation-origin shift). */
    public static Vector3f toJavaNormal(Vector3f bedrockNormal) {
        return new Vector3f(bedrockNormal.x, -bedrockNormal.y, bedrockNormal.z);
    }

    public static Matrix4f bedrockPixelsToRenderSpace() {
        return new Matrix4f()
                .translation(0.0F, PLAYER_PRESENTATION_ORIGIN_Y / PIXELS_PER_BLOCK, 0.0F)
                .scale(1.0F / PIXELS_PER_BLOCK, -1.0F / PIXELS_PER_BLOCK, 1.0F / PIXELS_PER_BLOCK);
    }

    /** Converts a Bedrock pixel-space scene matrix to the already converted VBU model space. */
    public static Matrix4f bedrockSceneToRenderSpace(Matrix4f bedrockScenePixels) {
        final Matrix4f bedrockToJavaPixels = new Matrix4f()
                .translation(0.0F, PLAYER_PRESENTATION_ORIGIN_Y, 0.0F)
                .scale(1.0F, -1.0F, 1.0F);
        final float inversePixels = 1.0F / PIXELS_PER_BLOCK;
        return new Matrix4f().scaling(inversePixels)
                .mul(bedrockScenePixels)
                .mul(bedrockToJavaPixels.invert())
                .scale(PIXELS_PER_BLOCK);
    }

    /**
     * Converts a Blockbench Bedrock-editor scene matrix to VBU render space. Blockbench has already
     * reflected imported Bedrock geometry on X, while VBU reflects it on Y, so this bridge is a
     * 180-degree Z rotation plus the shared player presentation origin. It is intentionally distinct
     * from {@link #bedrockSceneToRenderSpace(Matrix4f)}.
     */
    public static Matrix4f blockbenchSceneToRenderSpace(Matrix4f blockbenchScenePixels) {
        final Matrix4f blockbenchToJavaPixels = new Matrix4f()
                .translation(0.0F, PLAYER_PRESENTATION_ORIGIN_Y, 0.0F)
                .scale(-1.0F, -1.0F, 1.0F);
        final float inversePixels = 1.0F / PIXELS_PER_BLOCK;
        return new Matrix4f().scaling(inversePixels)
                .mul(blockbenchScenePixels)
                .mul(blockbenchToJavaPixels.invert())
                .scale(PIXELS_PER_BLOCK);
    }

    /** Converts a Blockbench-space displacement into the already converted VBU model space. */
    public static Vector3f blockbenchVectorToJavaModel(Vector3f blockbenchPixels) {
        return reflectedHostVector(blockbenchPixels);
    }

    /** Converts a Bedrock displacement into VBU's already converted Java model axes. */
    public static Vector3f bedrockBindingOffsetToJavaModel(Vector3f bedrockOffset) {
        return toJavaNormal(bedrockOffset);
    }

    /**
     * Converts a Bedrock bind-entity displacement into the owner attachment basis. This basis has
     * already crossed the Blockbench/Bedrock presentation bridge, unlike a captured ModelPart tree.
     */
    public static Vector3f bedrockBindingOffsetToOwnerAttachment(Vector3f bedrockOffset) {
        return reflectedHostVector(bedrockOffset);
    }

    public static Vector3f bedrockBindingOffset(Vector3f bedrockOffset, BindingOffsetFrame frame) {
        return switch (frame) {
            case JAVA_MODEL -> bedrockBindingOffsetToJavaModel(bedrockOffset);
            case OWNER_ATTACHMENT -> bedrockBindingOffsetToOwnerAttachment(bedrockOffset);
        };
    }

    /** Converts a Blockbench ZYX Euler rotation into VBU's ZYX model-space rotation. */
    public static Vector3f blockbenchRotationToJavaModel(Vector3f blockbenchDegrees) {
        return new Vector3f(-blockbenchDegrees.x, -blockbenchDegrees.y, blockbenchDegrees.z);
    }

    private static Vector3f reflectedHostVector(Vector3f vector) {
        return new Vector3f(-vector.x, -vector.y, vector.z);
    }

    /** Absolute-pivot deformation in the already converted Java model coordinate system. */
    public static Matrix4f deformation(Vector3f javaPivotPixels, Vector3f javaOffsetPixels,
                                       Vector3f rotationDegrees, Vector3f scale) {
        final float inv = 1.0F / PIXELS_PER_BLOCK;
        return new Matrix4f()
                .translate(javaOffsetPixels.x * inv, javaOffsetPixels.y * inv, javaOffsetPixels.z * inv)
                .translate(javaPivotPixels.x * inv, javaPivotPixels.y * inv, javaPivotPixels.z * inv)
                .rotateZYX((float) Math.toRadians(rotationDegrees.z),
                        (float) Math.toRadians(rotationDegrees.y),
                        (float) Math.toRadians(rotationDegrees.x))
                .scale(scale)
                .translate(-javaPivotPixels.x * inv, -javaPivotPixels.y * inv, -javaPivotPixels.z * inv);
    }

    public static Matrix4f hostDeformation(Matrix4f currentBoneWorld, Matrix4f bindBoneWorld) {
        return new Matrix4f(currentBoneWorld).mul(new Matrix4f(bindBoneWorld).invert());
    }

    /**
     * Places an attachable at the binding bone's bind-space anchor and then applies the bone's full
     * current deformation. The anchor is required even when the bind deformation is identity: Bedrock
     * item bones commonly have no bind rotation but still have a non-zero absolute pivot.
     */
    public static Matrix4f hostAttachment(Matrix4f currentBoneWorld, Matrix4f bindBoneWorld,
                                          Vector3f javaAnchorPixels) {
        return hostDeformation(currentBoneWorld, bindBoneWorld)
                .translate(javaAnchorPixels.x / PIXELS_PER_BLOCK,
                        javaAnchorPixels.y / PIXELS_PER_BLOCK,
                        javaAnchorPixels.z / PIXELS_PER_BLOCK);
    }

}
