package org.oryxel.viabedrockutility.renderer;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.attachable.AttachableHostContext;
import org.oryxel.viabedrockutility.attachable.BedrockTransformConvention;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Immutable world-space bone and locator matrices captured from an actual player render. */
public final class BedrockPlayerWorldPose {
    private final UUID ownerUuid;
    private final long tick;
    private final Map<String, Anchor> bones;
    private final Map<String, Anchor> locators;

    private BedrockPlayerWorldPose(UUID ownerUuid, long tick,
                                   Map<String, Anchor> bones, Map<String, Anchor> locators) {
        this.ownerUuid = ownerUuid;
        this.tick = tick;
        this.bones = immutableAnchors(bones);
        this.locators = immutableAnchors(locators);
    }

    public static BedrockPlayerWorldPose capture(UUID ownerUuid, long tick, Matrix4f worldPresentation,
                                                  BedrockPlayerModelMetadata metadata) {
        final AttachableHostContext host = new AttachableHostContext(metadata);
        return capture(ownerUuid, tick, worldPresentation, metadata,
                host::currentWorldMatrix, host::bindWorldMatrix);
    }

    static BedrockPlayerWorldPose capture(UUID ownerUuid, long tick, Matrix4f worldPresentation,
                                           BedrockPlayerModelMetadata metadata,
                                           Function<BedrockPlayerModelMetadata.Bone, Matrix4f> currentMatrix,
                                           Function<BedrockPlayerModelMetadata.Bone, Matrix4f> bindMatrix) {
        final Map<String, Anchor> boneAnchors = new LinkedHashMap<>();
        final Map<String, Anchor> locatorAnchors = new LinkedHashMap<>();
        for (BedrockPlayerModelMetadata.Bone bone : metadata.bones()) {
            final Matrix4f current = currentMatrix.apply(bone);
            final Matrix4f bind = bindMatrix.apply(bone);
            final Matrix4f boneModel = BedrockTransformConvention.hostAttachment(
                    current, bind, bone.pivot());
            boneAnchors.put(bone.key(), new Anchor(new Matrix4f(worldPresentation).mul(boneModel), false,
                    BedrockTransformConvention.BindingOffsetFrame.JAVA_MODEL));

            for (Map.Entry<String, BedrockPlayerModelMetadata.LocatorInfo> entry : bone.locators().entrySet()) {
                final BedrockPlayerModelMetadata.LocatorInfo locator = entry.getValue();
                Matrix4f locatorModel = BedrockTransformConvention.hostAttachment(
                        current, bind, locator.point());
                if (locator.ignoreInheritedScale()) {
                    final Vector3f translation = locatorModel.getTranslation(new Vector3f());
                    final Quaternionf rotation = locatorModel.getUnnormalizedRotation(new Quaternionf()).normalize();
                    locatorModel = new Matrix4f().translationRotateScale(
                            translation, rotation, new Vector3f(1.0F));
                }
                locatorAnchors.putIfAbsent(entry.getKey(), new Anchor(
                        new Matrix4f(worldPresentation).mul(locatorModel), locator.ignoreInheritedScale(),
                        BedrockTransformConvention.BindingOffsetFrame.JAVA_MODEL));
            }
        }
        return new BedrockPlayerWorldPose(ownerUuid, tick, boneAnchors, locatorAnchors);
    }

    public boolean isFresh(UUID ownerUuid, long currentTick) {
        return this.ownerUuid.equals(ownerUuid)
                && this.tick <= currentTick
                && this.tick >= currentTick - 1L;
    }

    public Anchor bone(String name) {
        return bones.get(BedrockPlayerModelMetadata.normalize(name));
    }

    public Anchor locator(String name) {
        return locators.get(BedrockPlayerModelMetadata.normalize(name));
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public long tick() {
        return tick;
    }

    private static Map<String, Anchor> immutableAnchors(Map<String, Anchor> source) {
        final Map<String, Anchor> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, new Anchor(
                value.matrix(), value.ignoreInheritedScale(), value.bindingOffsetFrame())));
        return Collections.unmodifiableMap(copy);
    }

    public record Anchor(Matrix4f matrix, boolean ignoreInheritedScale,
                         BedrockTransformConvention.BindingOffsetFrame bindingOffsetFrame) {
        public Anchor(Matrix4f matrix, boolean ignoreInheritedScale) {
            this(matrix, ignoreInheritedScale, BedrockTransformConvention.BindingOffsetFrame.JAVA_MODEL);
        }

        public Anchor {
            matrix = new Matrix4f(matrix);
            if (bindingOffsetFrame == null) {
                bindingOffsetFrame = BedrockTransformConvention.BindingOffsetFrame.JAVA_MODEL;
            }
        }

        @Override
        public Matrix4f matrix() {
            return new Matrix4f(matrix);
        }
    }
}
