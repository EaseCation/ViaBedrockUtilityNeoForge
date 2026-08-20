package org.oryxel.viabedrockutility.renderer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.cube.converter.model.element.Cube;
import org.cube.converter.model.element.Locator;
import org.cube.converter.model.element.Parent;
import org.cube.converter.util.element.Position3V;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.attachable.BedrockTransformConvention;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;

public final class BedrockPlayerModelMetadata {
    private static final Map<PlayerModel, BedrockPlayerModelMetadata> BY_MODEL = Collections.synchronizedMap(new WeakHashMap<>());

    private final Map<String, Bone> bones = new LinkedHashMap<>();
    private final boolean slim;

    public BedrockPlayerModelMetadata(boolean slim) {
        this.slim = slim;
    }

    public static void register(PlayerModel model, BedrockPlayerModelMetadata metadata) {
        BY_MODEL.put(model, metadata);
    }

    public static BedrockPlayerModelMetadata get(PlayerModel model) {
        return BY_MODEL.get(model);
    }

    public void addBone(Parent source, String modelName, String modelParent, ModelPart part) {
        addBone(source, modelName, modelParent, modelParent, part);
    }

    public void addBone(Parent source, String modelName, String semanticParent,
                        String presentationParent, ModelPart part) {
        String key = normalize(modelName);
        Map<String, LocatorInfo> locators = new HashMap<>();
        for (Map.Entry<String, Locator> entry : source.getLocators().entrySet()) {
            Locator locator = entry.getValue();
            locators.put(normalize(entry.getKey()), new LocatorInfo(
                    entry.getKey(),
                    toJavaModel(locator.getOffset()),
                    new Vector3f(locator.getRotation().getX(), locator.getRotation().getY(), locator.getRotation().getZ()),
                    locator.isIgnoreInheritedScale()
            ));
        }

        bones.put(key, new Bone(
                source.getName(),
                key,
                normalize(semanticParent),
                normalize(presentationParent),
                toJavaModel(source.getPivot()),
                new Vector3f(source.getRotation().getX(), source.getRotation().getY(), source.getRotation().getZ()),
                bounds(source),
                locators,
                part
        ));
    }

    public Bone bone(String name) {
        return bones.get(normalize(name));
    }

    public Bone firstBone(String... names) {
        for (String name : names) {
            Bone bone = bone(name);
            if (bone != null) {
                return bone;
            }
        }
        return null;
    }

    public LocatorInfo locator(Bone bone, String name) {
        return bone == null ? null : bone.locators().get(normalize(name));
    }

    public LocatorMatch findLocator(String name) {
        final String locatorKey = normalize(name);
        for (Bone bone : bones.values()) {
            final LocatorMatch match = locatorOn(bone, locatorKey);
            if (match != null) return match;
        }
        return null;
    }

    /** Ordered immutable view used when publishing a complete player pose snapshot. */
    public List<Bone> bones() {
        return List.copyOf(bones.values());
    }

    public boolean slim() {
        return this.slim;
    }

    public LocatorMatch findLocatorForArm(String name, Bone armBone, Bone itemBone) {
        String locatorKey = normalize(name);
        LocatorMatch match = locatorOn(armBone, locatorKey);
        if (match != null) {
            return match;
        }
        match = locatorOn(itemBone, locatorKey);
        if (match != null) {
            return match;
        }
        if (armBone != null) {
            for (Bone bone : descendantsOf(armBone)) {
                match = locatorOn(bone, locatorKey);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private LocatorMatch locatorOn(Bone bone, String locatorKey) {
        if (bone == null) {
            return null;
        }
        LocatorInfo locator = bone.locators().get(locatorKey);
        return locator == null ? null : new LocatorMatch(bone, locator);
    }

    public List<Bone> chainTo(Bone bone) {
        return chainTo(bone, Bone::parentKey);
    }

    public List<Bone> presentationChainTo(Bone bone) {
        return chainTo(bone, Bone::presentationParentKey);
    }

    private List<Bone> chainTo(Bone bone, Function<Bone, String> parentKey) {
        if (bone == null) {
            return List.of();
        }
        List<Bone> reversed = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Bone current = bone;
        while (current != null && visited.add(current.key())) {
            reversed.add(current);
            final String parent = parentKey.apply(current);
            current = parent.isBlank() ? null : bones.get(parent);
        }
        Collections.reverse(reversed);
        return reversed;
    }

    public Bounds directChildBounds(Bone parent) {
        if (parent == null) {
            return null;
        }
        Bounds merged = null;
        for (Bone bone : bones.values()) {
            if (bone.bounds() == null || !bone.parentKey().equals(parent.key())) {
                continue;
            }
            merged = merged == null ? bone.bounds() : merged.merge(bone.bounds());
        }
        return merged;
    }

    private List<Bone> descendantsOf(Bone parent) {
        List<Bone> descendants = new ArrayList<>();
        collectDescendants(parent, descendants, 0);
        return descendants;
    }

    private void collectDescendants(Bone parent, List<Bone> out, int depth) {
        if (parent == null || depth > 128) {
            return;
        }
        for (Bone bone : bones.values()) {
            if (!bone.parentKey().equals(parent.key())) {
                continue;
            }
            out.add(bone);
            collectDescendants(bone, out, depth + 1);
        }
    }

    public static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    public static Vector3f toJavaModel(Position3V position) {
        return new Vector3f(position.getX(),
                -position.getY() + BedrockTransformConvention.PLAYER_PRESENTATION_ORIGIN_Y, position.getZ());
    }

    private static Bounds bounds(Parent source) {
        if (source.getCubes().isEmpty()) {
            return null;
        }
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (Cube cube : source.getCubes().values()) {
            float x1 = cube.getPosition().getX();
            float y1 = cube.getPosition().getY();
            float z1 = cube.getPosition().getZ();
            float x2 = x1 + cube.getSize().getX();
            float y2 = y1 + cube.getSize().getY();
            float z2 = z1 + cube.getSize().getZ();
            minX = Math.min(minX, Math.min(x1, x2));
            minY = Math.min(minY, Math.min(y1, y2));
            minZ = Math.min(minZ, Math.min(z1, z2));
            maxX = Math.max(maxX, Math.max(x1, x2));
            maxY = Math.max(maxY, Math.max(y1, y2));
            maxZ = Math.max(maxZ, Math.max(z1, z2));
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public record Bone(String originalName, String key, String parentKey, String presentationParentKey,
                       Vector3f pivot, Vector3f rotation, Bounds bounds,
                       Map<String, LocatorInfo> locators, ModelPart part) {
    }

    public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        public Bounds merge(Bounds other) {
            return new Bounds(
                    Math.min(this.minX, other.minX),
                    Math.min(this.minY, other.minY),
                    Math.min(this.minZ, other.minZ),
                    Math.max(this.maxX, other.maxX),
                    Math.max(this.maxY, other.maxY),
                    Math.max(this.maxZ, other.maxZ)
            );
        }

        public Vector3f handFallbackPoint() {
            float height = this.maxY - this.minY;
            return toJavaModel(new Position3V(
                    (this.minX + this.maxX) * 0.5F,
                    this.minY + height * 0.25F,
                    (this.minZ + this.maxZ) * 0.5F + 1.0F
            ));
        }
    }

    public record LocatorInfo(String originalName, Vector3f point, Vector3f rotation, boolean ignoreInheritedScale) {
    }

    public record LocatorMatch(Bone bone, LocatorInfo locator) {
    }
}
