package org.oryxel.viabedrockutility.util;

import com.google.common.collect.Maps;
import net.minecraft.client.model.*;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.core.Direction;
import org.cube.converter.model.element.Cube;
import org.cube.converter.model.element.Parent;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;
import org.cube.converter.util.element.Position3V;
import org.cube.converter.util.element.UVMap;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.fabric.ViaBedrockUtilityFabric;
import org.oryxel.viabedrockutility.mixin.interfaces.ICuboid;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;
import org.oryxel.viabedrockutility.renderer.model.CustomEntityModel;

import java.util.*;

public final class GeometryUtil {
    private static final List<String> LEG_RELATED = List.of("leftleg", "rightleg", "rightpants", "leftpants");

    public static Model buildModel(final BedrockGeometryModel geometry, final boolean player, boolean slim) {
        return buildModel(geometry, player, slim, null);
    }

    public static Model buildModel(final BedrockGeometryModel geometry, final boolean player, boolean slim, final String geometryName) {
        // There are some times when the skin image file is larger than the geometry UV points.
        // In this case, we need to scale UV calls
        // https://github.com/Camotoy/BedrockSkinUtility/issues/9
        final float uvWidth = geometry.getTextureSize().getX();
        final float uvHeight = geometry.getTextureSize().getY();

        // Pre-compute which bones are leg-related (including all descendants of leg bones).
        // Child bones of legs must use the same Y coordinate system as their parent leg bone;
        // otherwise the Y-inversion transform (-Y + 24.016) causes severe height offset.
        final Set<String> legRelatedBones = new HashSet<>();
        final Map<String, String> boneParentMap = new LinkedHashMap<>();
        final Map<String, Float> bonePivotY = new HashMap<>();
        if (player) {
            for (final Parent bone : geometry.getParents()) {
                String bn = bone.getName().toLowerCase(Locale.ROOT);
                String pn = bone.getParent() != null ? bone.getParent().toLowerCase(Locale.ROOT) : "";
                boneParentMap.put(bn, pn);
                bonePivotY.put(bn, bone.getPivot().getY());
            }
            for (String bn : boneParentMap.keySet()) {
                if (LEG_RELATED.contains(bn)) {
                    legRelatedBones.add(bn);
                }
            }
            boolean changed = true;
            while (changed) {
                changed = false;
                for (Map.Entry<String, String> entry : boneParentMap.entrySet()) {
                    if (!legRelatedBones.contains(entry.getKey()) && legRelatedBones.contains(entry.getValue())) {
                        legRelatedBones.add(entry.getKey());
                        changed = true;
                    }
                }
            }
        }

        final Map<String, PartInfo> stringToPart = new HashMap<>();
        for (final Parent bone : geometry.getParents()) {
            final Map<String, ModelPart> children = Maps.newHashMap();
            final ModelPart part = new ModelPart(List.of(), children);

            boolean neededOffset = switch (bone.getName().toLowerCase(Locale.ROOT)) {
                case "rightarm", "leftarm" -> player;
                default -> false;
            };

            ((IModelPart)((Object)part)).viaBedrockUtility$setVBUModel();
            ((IModelPart)((Object)part)).viaBedrockUtility$setName(bone.getName());
            ((IModelPart)((Object)part)).viaBedrockUtility$setNeededOffset(neededOffset);
            ((IModelPart)((Object)part)).viaBedrockUtility$setAngles(new Vector3f(bone.getRotation().getX() , bone.getRotation().getY(), bone.getRotation().getZ()));

            boolean isLeg = player && legRelatedBones.contains(bone.getName().toLowerCase(Locale.ROOT));
            if (isLeg) {
                float pivotY = bone.getPivot().getY();
                // Vanilla translateAndRotate translates to origin but never translates back,
                // so child origins accumulate with parents. Use relative Y to compensate:
                // accumulated origin Y of any bone = its absolute Bedrock pivot Y.
                String parentLower = boneParentMap.getOrDefault(bone.getName().toLowerCase(Locale.ROOT), "");
                float parentPivotY = bonePivotY.getOrDefault(parentLower, 0f);
                part.setPos(0, pivotY - parentPivotY, 0);
                part.setInitialPose(part.storePose());
            } else {
                ((IModelPart)((Object)part)).viaBedrockUtility$setPivot(new Vector3f(bone.getPivot().getX(), -bone.getPivot().getY() + 24.016F, bone.getPivot().getZ()));
            }

            // Java don't allow individual cubes to have their own rotation therefore, we have to separate each cube into ModelPart to be able to rotate.
            for (final Cube cube : bone.getCubes().values()) {
                final Position3V pos = cube.getPosition();

                final float sizeX = cube.getSize().getX(), sizeY = cube.getSize().getY(), sizeZ = cube.getSize().getZ();
                final float inflate = cube.getInflate();

                final UVMap uvMap = cube.getUvMap().clone();

                // Negative size inverts vertex positions, swapping which face is on which side.
                // Swap UV assignments so textures remain on the correct geometric faces.
                // Y axis is not swapped here because the existing Bedrock UP/DOWN convention swap
                // in correctUv already compensates for the Y inversion.
                if (sizeX < 0) {
                    swapUv(uvMap, org.cube.converter.util.element.Direction.EAST, org.cube.converter.util.element.Direction.WEST);
                }
                if (sizeZ < 0) {
                    swapUv(uvMap, org.cube.converter.util.element.Direction.NORTH, org.cube.converter.util.element.Direction.SOUTH);
                }

                final Set<Direction> set = new HashSet<>();
                for (final Direction direction : Direction.values()) {
                    if (uvMap.getMap().containsKey(org.cube.converter.util.element.Direction.values()[direction.ordinal()])) {
                        set.add(direction);
                    }
                }

                final ModelPart.Cube cuboid = new ModelPart.Cube(0, 0, pos.getX(), isLeg ? pos.getY() : -(pos.getY() - 24.016F + sizeY), pos.getZ(), sizeX, sizeY, sizeZ, inflate, inflate, inflate, cube.isMirror(), uvWidth, uvHeight, set);
                correctUv(cuboid, set, uvMap, uvWidth, uvHeight, cube.getInflate(), cube.isMirror());
                ((ICuboid)(Object) cuboid).viaBedrockUtility$markAsVBU();

                final ModelPart cubePart = new ModelPart(List.of(cuboid), Map.of());
                ((IModelPart)((Object)cubePart)).viaBedrockUtility$setPivot(new Vector3f(cube.getPivot().getX(), -cube.getPivot().getY() + 24.016F, cube.getPivot().getZ()));
                ((IModelPart)((Object)cubePart)).viaBedrockUtility$setAngles(new Vector3f(cube.getRotation().getX(), cube.getRotation().getY(), cube.getRotation().getZ()));
                ((IModelPart)((Object)cubePart)).viaBedrockUtility$setVBUModel();
                ((IModelPart)((Object)cubePart)).viaBedrockUtility$setNeededOffset(neededOffset);
                ((IModelPart)((Object)cubePart)).viaBedrockUtility$setName(bone.getName());
                children.put(cube.getParent() + cube.hashCode(), cubePart);
            }

            String parent = bone.getParent();
            String name = bone.getName();
            if (player) {
                switch (name.toLowerCase(Locale.ROOT)) { // Also do this with the overlays? Those are final, though.
                    case "head", "rightarm", "body", "leftarm", "leftleg", "rightleg" -> parent = "root";
                }
            }

            stringToPart.put(adjustFormatting(player, name), new PartInfo(adjustFormatting(player, parent), part, children));
        }

        PartInfo root = stringToPart.get("root");
        if (root == null) {
            final Map<String, ModelPart> rootParts = Maps.newHashMap();
            final ModelPart rootPart = new ModelPart(List.of(), rootParts);
            ((IModelPart)((Object)rootPart)).viaBedrockUtility$setVBUModel();
            stringToPart.put("root", root = new PartInfo("", rootPart, rootParts));
        } else if (!player) {
            final Map<String, ModelPart> rootParts = Maps.newHashMap();
            root = new PartInfo("", new ModelPart(List.of(), rootParts), rootParts);
        }

        // Detect all cycles in the parent graph (handles A鈫扐, A鈫払鈫扐, A鈫払鈫扖鈫扐, etc.)
        final Map<String, String> parentGraph = new HashMap<>();
        for (Map.Entry<String, PartInfo> entry : stringToPart.entrySet()) {
            if (!entry.getValue().parent.isBlank()) {
                parentGraph.put(entry.getKey(), entry.getValue().parent);
            }
        }

        final Set<String> cyclicBones = new HashSet<>();
        final Set<String> processed = new HashSet<>();
        for (String bone : parentGraph.keySet()) {
            if (processed.contains(bone)) continue;
            final Set<String> path = new LinkedHashSet<>();
            String current = bone;
            while (current != null && !processed.contains(current)) {
                if (path.contains(current)) {
                    boolean inCycle = false;
                    final List<String> cycleMembers = new ArrayList<>();
                    for (String p : path) {
                        if (p.equals(current)) inCycle = true;
                        if (inCycle) cycleMembers.add(p);
                    }
                    cyclicBones.addAll(cycleMembers);
                    ViaBedrockUtilityFabric.LOGGER.warn(
                            "[GeometryUtil] Detected circular parent chain: {} 鈥?breaking cycle by attaching to root",
                            String.join(" 鈫?", cycleMembers) + " 鈫?" + current);
                    break;
                }
                path.add(current);
                current = parentGraph.get(current);
            }
            processed.addAll(path);
        }

        for (Map.Entry<String, PartInfo> entry : stringToPart.entrySet()) {
            if (entry.getValue().parent.isBlank() && entry.getValue().part() != root.part) {
                root.children.put(entry.getKey(), entry.getValue().part());
                continue;
            }

            if (cyclicBones.contains(entry.getKey())) {
                root.children.put(entry.getKey(), entry.getValue().part());
                continue;
            }

            // The tree root must not be re-added as a child of any other bone
            // (e.g. Bedrock skins with "world" 鈫?"root" hierarchy where "root" is already the tree root)
            if (entry.getValue().part() == root.part()) {
                continue;
            }

            PartInfo parentPart = stringToPart.get(entry.getValue().parent);
            if (parentPart != null) {
                parentPart.children.put(entry.getKey(), entry.getValue().part);
            }
        }

        // Validate the actual ModelPart tree for cycles (identity-based, not name-based)
        validateAndFixCycles(root.part(), "root", new IdentityHashMap<>(), new ArrayList<>(), geometryName);

        return player ? new PlayerModel(root.part(), slim) : new CustomEntityModel<>(root.part());
    }

    private static String adjustFormatting(boolean player, String name) {
        if (!player) {
            return name;
        }

        if (name == null) {
            return null;
        }

        return switch (name.toLowerCase(Locale.ROOT)) {
            case "leftarm" -> "left_arm";
            case "rightarm" -> "right_arm";
            case "leftleg" -> "left_leg";
            case "rightleg" -> "right_leg";
            default -> name.toLowerCase(Locale.ROOT);
        };
    }

    /**
     * DFS validation of the actual ModelPart tree using object identity.
     * Detects and removes cyclic edges that the name-based detection might miss.
     */
    private static void validateAndFixCycles(ModelPart part, String name, IdentityHashMap<ModelPart, String> ancestors, List<String> path, String geometryName) {
        if (ancestors.containsKey(part)) {
            // This ModelPart object is already an ancestor 鈥?cycle detected!
            String cyclePath = String.join(" 鈫?", path) + " 鈫?" + name + " (CYCLE to '" + ancestors.get(part) + "')";
            ViaBedrockUtilityFabric.LOGGER.error(
                    "[GeometryUtil] RUNTIME ModelPart CYCLE DETECTED in geometry '{}': {}",
                    geometryName != null ? geometryName : "unknown", cyclePath);
            return; // caller will remove this edge
        }

        ancestors.put(part, name);
        path.add(name);

        Map<String, ModelPart> children = ((IModelPart) ((Object) part)).viaBedrockUtility$getChildren();
        Iterator<Map.Entry<String, ModelPart>> it = children.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ModelPart> entry = it.next();
            if (ancestors.containsKey(entry.getValue())) {
                // Child points to an ancestor 鈥?remove this cyclic edge
                String cyclePath = String.join(" 鈫?", path) + " 鈫?" + entry.getKey() + " (CYCLE to '" + ancestors.get(entry.getValue()) + "')";
                ViaBedrockUtilityFabric.LOGGER.error(
                        "[GeometryUtil] Removing cyclic ModelPart edge in geometry '{}': {}",
                        geometryName != null ? geometryName : "unknown", cyclePath);
                it.remove();
            } else {
                validateAndFixCycles(entry.getValue(), entry.getKey(), ancestors, path, geometryName);
            }
        }

        path.remove(path.size() - 1);
        ancestors.remove(part);
    }

    private record PartInfo(String parent, ModelPart part, Map<String, ModelPart> children) {
    }

    private static void swapUv(UVMap map, org.cube.converter.util.element.Direction a, org.cube.converter.util.element.Direction b) {
        Float[] uvA = map.getMap().remove(a);
        Float[] uvB = map.getMap().remove(b);
        // Flip U (swap u1鈫攗2) to compensate for reversed vertex winding on the swapped face.
        // Each face pair (EAST/WEST, NORTH/SOUTH) has opposite vertex ordering along one axis,
        // so placing one face's UV on the other requires a horizontal flip.
        if (uvA != null) map.getMap().put(b, new Float[]{uvA[2], uvA[1], uvA[0], uvA[3]});
        if (uvB != null) map.getMap().put(a, new Float[]{uvB[2], uvB[1], uvB[0], uvB[3]});
    }

    private static void correctUv(final ModelPart.Cube cuboid, final Set<Direction> set, final UVMap map, final float uvWidth, final float uvHeight, final float inflate, final boolean mirror) {
        float x = cuboid.minX, y = cuboid.minY, z = cuboid.minZ;
        float f = cuboid.maxX, g = cuboid.maxY, h = cuboid.maxZ;

        x -= inflate;
        y -= inflate;
        z -= inflate;
        f += inflate;
        g += inflate;
        h += inflate;

        if (mirror) {
            float i = f;
            f = x;
            x = i;
        }

        ModelPart.Vertex vertex = new ModelPart.Vertex(x, y, z, 0.0F, 0.0F);
        ModelPart.Vertex vertex2 = new ModelPart.Vertex(f, y, z, 0.0F, 8.0F);
        ModelPart.Vertex vertex3 = new ModelPart.Vertex(f, g, z, 8.0F, 8.0F);
        ModelPart.Vertex vertex4 = new ModelPart.Vertex(x, g, z, 8.0F, 0.0F);
        ModelPart.Vertex vertex5 = new ModelPart.Vertex(x, y, h, 0.0F, 0.0F);
        ModelPart.Vertex vertex6 = new ModelPart.Vertex(f, y, h, 0.0F, 8.0F);
        ModelPart.Vertex vertex7 = new ModelPart.Vertex(f, g, h, 8.0F, 8.0F);
        ModelPart.Vertex vertex8 = new ModelPart.Vertex(x, g, h, 8.0F, 0.0F);

        final ModelPart.Polygon[] sides = cuboid.polygons;
        int s = 0;

        if (set.contains(Direction.DOWN)) {
            // Bedrock UP/DOWN texture regions are swapped vs Java; swap UV if both faces exist (box UV)
            Float[] uv = map.getMap().get(org.cube.converter.util.element.Direction.UP);
            if (uv == null) uv = map.getMap().get(org.cube.converter.util.element.Direction.DOWN);
            // Swap both u1鈫攗2 and v1鈫攙2 to compensate for scale(-1,-1,1): X negation flips U,
            // and Y negation flips the viewing side which flips V on horizontal faces
            sides[s++] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex6, vertex5, vertex, vertex2}, uv[2], uv[3], uv[0], uv[1], uvWidth, uvHeight, mirror, Direction.DOWN);
        }

        if (set.contains(Direction.UP)) {
            // Bedrock UP/DOWN texture regions are swapped vs Java; swap UV if both faces exist (box UV)
            Float[] uv = map.getMap().get(org.cube.converter.util.element.Direction.DOWN);
            if (uv == null) uv = map.getMap().get(org.cube.converter.util.element.Direction.UP);
            // Swap both u1鈫攗2 and v1鈫攙2 to compensate for scale(-1,-1,1): X negation flips U,
            // and Y negation flips the viewing side which flips V on horizontal faces
            sides[s++] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex3, vertex4, vertex8, vertex7}, uv[2], uv[3], uv[0], uv[1], uvWidth, uvHeight, mirror, Direction.UP);
        }

        if (set.contains(Direction.WEST)) {
            final Float[] uv = map.getMap().get(org.cube.converter.util.element.Direction.WEST);
            // Swap u1/u2 to compensate for horizontal flip caused by global scale(-1,-1,1) Z negation
            sides[s++] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex, vertex5, vertex8, vertex4}, uv[2], uv[1], uv[0], uv[3], uvWidth, uvHeight, mirror, Direction.WEST);
        }

        if (set.contains(Direction.NORTH)) {
            final Float[] uv = map.getMap().get(org.cube.converter.util.element.Direction.NORTH);
            sides[s++] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex2, vertex, vertex4, vertex3}, uv[0], uv[1], uv[2], uv[3], uvWidth, uvHeight, mirror, Direction.NORTH);
        }

        if (set.contains(Direction.EAST)) {
            final Float[] uv = map.getMap().get(org.cube.converter.util.element.Direction.EAST);
            // Swap u1/u2 to compensate for horizontal flip caused by global scale(-1,-1,1) Z negation
            sides[s++] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex6, vertex2, vertex3, vertex7}, uv[2], uv[1], uv[0], uv[3], uvWidth, uvHeight, mirror, Direction.EAST);
        }

        if (set.contains(Direction.SOUTH)) {
            final Float[] uv = map.getMap().get(org.cube.converter.util.element.Direction.SOUTH);
            sides[s] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex5, vertex6, vertex7, vertex8}, uv[0], uv[1], uv[2], uv[3], uvWidth, uvHeight, mirror, Direction.SOUTH);
        }
    }

    private static Direction normalToDirection(float nx, float ny, float nz, boolean isLeg) {
        if (!isLeg) ny = -ny; // Y axis is inverted for non-leg bones
        float absX = Math.abs(nx), absY = Math.abs(ny), absZ = Math.abs(nz);
        if (absY >= absX && absY >= absZ) {
            return ny > 0 ? Direction.UP : Direction.DOWN;
        } else if (absX >= absZ) {
            return nx > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return nz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

}
