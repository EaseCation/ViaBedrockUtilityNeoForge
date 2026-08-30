package org.oryxel.viabedrockutility.attachable;

import com.mojang.blaze3d.vertex.PoseStack;
import net.easecation.bedrockmotion.pack.PackManager;
import net.easecation.bedrockmotion.pack.definitions.AttachableDefinitions;
import net.easecation.bedrockmotion.pack.definitions.VisibleBounds;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import org.cube.converter.model.element.Cube;
import org.cube.converter.model.element.Parent;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.util.GeometryUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Detached (GROUND/FIXED display context) attachable rendering and its generation-scoped model cache. */
final class DetachedAttachableRenderer {
    private final Map<DetachedKey, DetachedModel> detachedModels = new HashMap<>();

    boolean render(AttachableItemSnapshot item, ItemDisplayContext displayContext,
                   PoseStack poses, MultiBufferSource buffers,
                   int packedLight, int packedOverlay) {
        if (item == null || item.isEmpty() || !isDetachedDisplayContext(displayContext)) {
            return false;
        }
        final ViaBedrockUtility.PackGeneration generation = ViaBedrockUtility.getInstance().getPackGeneration();
        final PackManager packs = generation.manager();
        if (packs == null || packs.getAttachableDefinitions() == null) {
            return false;
        }
        final List<AttachableDefinitions.AttachableDefinition> candidates =
                packs.getAttachableDefinitions().candidatesFor(item.itemIdentifier().toString());
        if (candidates.isEmpty()) {
            return false;
        }
        if (candidates.size() > 1) {
            AttachableDebugLog.warnOnce(item.itemIdentifier() + ":detached-ambiguous",
                    "[Attachable] Multiple detached definitions matched " + item.itemIdentifier()
                            + "; using " + candidates.getFirst().identifier(), null);
        }
        final AttachableDefinitions.AttachableDefinition definition = candidates.getFirst();
        final String geometryName = defaultValue(definition.data().getGeometries());
        final String textureName = defaultValue(definition.data().getTextures());
        if (geometryName == null || textureName == null) {
            return false;
        }
        final BedrockGeometryModel geometry = packs.getModelDefinitions().getEntityModels().get(geometryName);
        if (geometry == null) {
            AttachableDebugLog.warnOnce(definition.identifier() + ":detached-geometry:" + geometryName,
                    "[Attachable] Missing detached geometry " + geometryName, null);
            return false;
        }

        final DetachedKey key = new DetachedKey(generation.generation(), definition.identifier(), geometryName, textureName);
        final DetachedModel detached = detachedModels.computeIfAbsent(key, ignored -> new DetachedModel(
                GeometryUtil.buildAttachableModel(geometry, geometryName,
                        alias -> AttachableTextureResolver.resolve(
                                packs, definition.data().getTextures(), alias, textureName)),
                detachedBounds(packs, geometryName, geometry)));
        final ResourceLocation texture = ResourceLocation.parse(textureName.toLowerCase(Locale.ROOT));
        final String material = defaultValue(definition.data().getMaterials());
        final RenderType renderType = material != null && material.toLowerCase(Locale.ROOT).contains("blend")
                ? RenderType.entityTranslucent(texture) : RenderType.entityCutoutNoCull(texture);

        poses.pushPose();
        try {
            final DetachedBounds bounds = detached.bounds();
            final float extent = Math.max(bounds.width(), Math.max(bounds.height(), bounds.depth()));
            final float scale = extent <= 1.0E-5F ? 1.0F : 0.85F / extent;
            poses.scale(scale, scale, scale);
            poses.translate(-bounds.centerX(), -bounds.centerY(), -bounds.centerZ());
            detached.model().renderToBuffer(poses, buffers.getBuffer(renderType), packedLight, packedOverlay);
        } finally {
            poses.popPose();
        }
        return true;
    }

    void clear() {
        detachedModels.clear();
    }

    static boolean isDetachedDisplayContext(ItemDisplayContext displayContext) {
        return displayContext == ItemDisplayContext.GROUND || displayContext == ItemDisplayContext.FIXED;
    }

    private static String defaultValue(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        final String value = values.get("default");
        return value == null ? values.values().iterator().next() : value;
    }

    private static DetachedBounds detachedBounds(PackManager packs, String geometryName,
                                                  BedrockGeometryModel geometry) {
        final VisibleBounds visible = packs.getModelDefinitions().getVisibleBoundsMap().get(geometryName);
        if (visible != null) {
            final float centerY = BedrockTransformConvention.PLAYER_PRESENTATION_ORIGIN_Y
                    / BedrockTransformConvention.PIXELS_PER_BLOCK - visible.offsetY();
            return DetachedBounds.fromCenter(visible.offsetX(), centerY, visible.offsetZ(),
                    visible.width(), visible.height(), visible.width());
        }
        return defaultPoseBounds(geometry);
    }

    private static DetachedBounds defaultPoseBounds(BedrockGeometryModel geometry) {
        final Map<String, Parent> bones = new HashMap<>();
        geometry.getParents().forEach(bone -> bones.put(bone.getName(), bone));
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        final Vector3f point = new Vector3f();
        for (Parent bone : geometry.getParents()) {
            final Matrix4f boneMatrix = defaultBoneMatrix(bone, bones);
            for (Cube cube : bone.getCubes().values()) {
                final Matrix4f matrix = new Matrix4f(boneMatrix).mul(BedrockTransformConvention.deformation(
                        BedrockTransformConvention.toJavaModel(new Vector3f(cube.getPivot().getX(),
                                cube.getPivot().getY(), cube.getPivot().getZ())), new Vector3f(),
                        new Vector3f(cube.getRotation().getX(), cube.getRotation().getY(), cube.getRotation().getZ()),
                        new Vector3f(1.0F)));
                for (int mask = 0; mask < 8; mask++) {
                    final float x = cube.getPosition().getX() + ((mask & 1) == 0 ? 0.0F : cube.getSize().getX());
                    final float y = cube.getPosition().getY() + ((mask & 2) == 0 ? 0.0F : cube.getSize().getY());
                    final float z = cube.getPosition().getZ() + ((mask & 4) == 0 ? 0.0F : cube.getSize().getZ());
                    point.set(BedrockTransformConvention.toJavaModel(new Vector3f(x, y, z)))
                            .div(BedrockTransformConvention.PIXELS_PER_BLOCK);
                    matrix.transformPosition(point);
                    minX = Math.min(minX, point.x); minY = Math.min(minY, point.y); minZ = Math.min(minZ, point.z);
                    maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y); maxZ = Math.max(maxZ, point.z);
                }
            }
        }
        return Float.isFinite(minX) ? new DetachedBounds(minX, minY, minZ, maxX, maxY, maxZ)
                : DetachedBounds.fromCenter(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
    }

    private static Matrix4f defaultBoneMatrix(Parent bone, Map<String, Parent> bones) {
        final ArrayList<Parent> chain = new ArrayList<>();
        Parent current = bone;
        for (int guard = 0; current != null && guard < 128; guard++) {
            chain.add(current);
            current = current.getParent().isBlank() ? null : bones.get(current.getParent());
        }
        java.util.Collections.reverse(chain);
        final Matrix4f matrix = new Matrix4f();
        for (Parent entry : chain) {
            matrix.mul(BedrockTransformConvention.deformation(
                    BedrockTransformConvention.toJavaModel(new Vector3f(entry.getPivot().getX(),
                            entry.getPivot().getY(), entry.getPivot().getZ())), new Vector3f(),
                    new Vector3f(entry.getRotation().getX(), entry.getRotation().getY(), entry.getRotation().getZ()),
                    new Vector3f(1.0F)));
        }
        return matrix;
    }

    private record DetachedKey(long generation, String attachableIdentifier, String geometryIdentifier,
                               String textureIdentifier) {
    }

    private record DetachedModel(Model model, DetachedBounds bounds) {
    }

    private record DetachedBounds(float minX, float minY, float minZ,
                                  float maxX, float maxY, float maxZ) {
        static DetachedBounds fromCenter(float x, float y, float z,
                                         float width, float height, float depth) {
            return new DetachedBounds(x - width * 0.5F, y - height * 0.5F, z - depth * 0.5F,
                    x + width * 0.5F, y + height * 0.5F, z + depth * 0.5F);
        }

        float centerX() { return (minX + maxX) * 0.5F; }
        float centerY() { return (minY + maxY) * 0.5F; }
        float centerZ() { return (minZ + maxZ) * 0.5F; }
        float width() { return maxX - minX; }
        float height() { return maxY - minY; }
        float depth() { return maxZ - minZ; }
    }
}
