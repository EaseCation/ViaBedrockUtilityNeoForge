package org.oryxel.viabedrockutility.util;

import net.minecraft.client.model.*;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.core.Direction;
import org.cube.converter.model.element.Cube;
import org.cube.converter.model.element.Parent;
import org.cube.converter.model.element.PolyMesh;
import org.cube.converter.model.element.TextureMesh;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;
import org.cube.converter.util.element.Position3V;
import org.cube.converter.util.element.UVMap;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import org.oryxel.viabedrockutility.mixin.interfaces.ICuboid;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;
import org.oryxel.viabedrockutility.renderer.BedrockPlayerModelMetadata;
import org.oryxel.viabedrockutility.renderer.model.CustomEntityModel;
import org.oryxel.viabedrockutility.attachable.BedrockTransformConvention;

import java.util.*;
import java.util.function.Function;
import java.awt.image.BufferedImage;

public final class GeometryUtil {
    // One kill switch restores the pre-Stage-2 one-cuboid-per-part topology and vanilla tree walk.
    private static final boolean GROUP_CUBOIDS =
            !Boolean.getBoolean("viabedrockutility.disableIndexedRender");

    public static Model buildModel(final BedrockGeometryModel geometry, final boolean player, boolean slim) {
        return buildModel(geometry, player, slim, null, false, null);
    }

    public static Model buildModel(final BedrockGeometryModel geometry, final boolean player, boolean slim, final String geometryName) {
        return buildModel(geometry, player, slim, geometryName, false, null);
    }

    /** Builds an attachable geometry for an already resolved hand anchor. */
    public static Model buildAttachableModel(final BedrockGeometryModel geometry, final String geometryName) {
        return buildAttachableModel(geometry, geometryName, null);
    }

    /**
     * Builds an attachable and, when texture data is available, adds Bedrock's alpha-derived
     * voxel boundary faces for texture_meshes. Texture alpha is intentionally resolved at runtime
     * because an attachable render-controller pass selects the texture independently of geometry.
     */
    public static Model buildAttachableModel(final BedrockGeometryModel geometry, final String geometryName,
                                             final Function<String, TextureAlpha> textureResolver) {
        // Ordinary cubes/poly_meshes keep the long-standing absolute Bedrock geometry contract.
        // Only texture_meshes are local sprite sheets installed below the resolved hand anchor.
        return buildModel(geometry, false, false, geometryName, false, textureResolver);
    }

    private static Model buildModel(final BedrockGeometryModel geometry, final boolean player, boolean slim,
                                    final String geometryName, final boolean localGeometry,
                                    final Function<String, TextureAlpha> textureResolver) {
        // There are some times when the skin image file is larger than the geometry UV points.
        // In this case, we need to scale UV calls
        // https://github.com/Camotoy/BedrockSkinUtility/issues/9
        final float uvWidth = geometry.getTextureSize().getX();
        final float uvHeight = geometry.getTextureSize().getY();

        final BedrockPlayerModelMetadata playerMetadata = player ? new BedrockPlayerModelMetadata(slim) : null;
        final Map<String, PartInfo> stringToPart = new LinkedHashMap<>();
        for (final Parent bone : geometry.getParents()) {
            // ModelPart renders children in map iteration order. Keep cube transform groups in source order
            // so translucent geometry retains the same vertex submission order as the ungrouped model.
            final Map<String, ModelPart> children = new LinkedHashMap<>();
            final List<CuboidGroup> cuboidGroups = new ArrayList<>();

            boolean neededOffset = switch (bone.getName().toLowerCase(Locale.ROOT)) {
                case "rightarm", "leftarm" -> player;
                default -> false;
            };

            // Cubes with the same adjacent transform can share one ModelPart. Do not merge non-adjacent
            // groups: that would reorder vertices around intervening groups and break translucent models.
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
                    if (uvMap.getUvMap().containsKey(org.cube.converter.util.element.Direction.values()[direction.ordinal()])) {
                        set.add(direction);
                    }
                }

                final Vector3f javaPos = toJavaGeometry(
                        new Vector3f(pos.getX(), pos.getY(), pos.getZ()), localGeometry);
                final ModelPart.Cube cuboid = new ModelPart.Cube(0, 0, javaPos.x,
                        javaPos.y - sizeY,
                        javaPos.z, sizeX, sizeY, sizeZ, inflate, inflate, inflate,
                        cube.isMirror(), uvWidth, uvHeight, set);
                correctUv(cuboid, set, uvMap, uvWidth, uvHeight, cube.getInflate(), cube.isMirror());
                markAsVbuBox(cuboid, inflate, cube.isMirror());
                appendCuboid(cuboidGroups, CuboidTransform.from(cube, localGeometry), cuboid);
            }

            // poly_mesh vertices are already absolute and use the identity transform. Appending them after
            // boxes preserves their previous relative submission position; an adjacent identity box group
            // may absorb them without changing order.
            if (bone.getPolyMesh() != null) {
                buildPolyMeshCuboids(bone.getPolyMesh(), uvWidth, uvHeight, cuboidGroups, localGeometry);
            }
            if (textureResolver != null && !bone.getTextureMeshes().isEmpty()) {
                buildTextureMeshBoundaryCuboids(bone.getTextureMeshes(), uvWidth, uvHeight,
                        textureResolver, cuboidGroups);
            }

            int groupIndex = 0;
            for (CuboidGroup group : cuboidGroups) {
                final ModelPart cubePart = new ModelPart(List.copyOf(group.cuboids), Map.of());
                final IModelPart cubePartExtension = (IModelPart) (Object) cubePart;
                cubePartExtension.viaBedrockUtility$setPivot(group.transform.javaPivot(localGeometry));
                cubePartExtension.viaBedrockUtility$setAngles(group.transform.rotation());
                cubePartExtension.viaBedrockUtility$setVBUModel();
                cubePartExtension.viaBedrockUtility$setCubeGroup();
                cubePartExtension.viaBedrockUtility$setNeededOffset(neededOffset);
                cubePartExtension.viaBedrockUtility$setName(bone.getName());
                children.put("\u0000vbu_cube_group_" + groupIndex++, cubePart);
            }

            // Keep cuboids in child parts so independently transformed groups can share one batch. The
            // indexed renderer treats these internal parts as the owning bone's cuboids for visible and
            // skipDraw semantics while continuing to traverse real child bones exactly like vanilla.
            final ModelPart part = new ModelPart(List.of(), children);
            final IModelPart partExtension = (IModelPart) (Object) part;
            partExtension.viaBedrockUtility$setVBUModel();
            partExtension.viaBedrockUtility$setName(bone.getName());
            partExtension.viaBedrockUtility$setNeededOffset(neededOffset);
            partExtension.viaBedrockUtility$setAngles(new Vector3f(bone.getRotation().getX(), bone.getRotation().getY(), bone.getRotation().getZ()));

            // Entity/player geometry uses the absolute presentation origin. An attachable is local
            // to its resolved host bone and therefore only reflects Bedrock's Y axis here.
            partExtension.viaBedrockUtility$setPivot(toJavaGeometry(new Vector3f(
                    bone.getPivot().getX(), bone.getPivot().getY(), bone.getPivot().getZ()), localGeometry));

            final String semanticParent = bone.getParent();
            String name = bone.getName();
            String renderParent = presentationParent(player, name, semanticParent);

            String adjustedName = adjustFormatting(player, name);
            String adjustedSemanticParent = adjustFormatting(player, semanticParent);
            String adjustedRenderParent = adjustFormatting(player, renderParent);
            if (playerMetadata != null) {
                playerMetadata.addBone(bone, adjustedName, adjustedSemanticParent, adjustedRenderParent, part);
            }
            stringToPart.put(adjustedName, new PartInfo(adjustedRenderParent, part, children));
        }

        PartInfo root = stringToPart.get("root");
        if (root == null) {
            final Map<String, ModelPart> rootParts = new LinkedHashMap<>();
            final ModelPart rootPart = new ModelPart(List.of(), rootParts);
            ((IModelPart)((Object)rootPart)).viaBedrockUtility$setVBUModel();
            stringToPart.put("root", root = new PartInfo("", rootPart, rootParts));
        } else if (!player) {
            final Map<String, ModelPart> rootParts = new LinkedHashMap<>();
            final ModelPart rootPart = new ModelPart(List.of(), rootParts);
            ((IModelPart) (Object) rootPart).viaBedrockUtility$setVBUModel();
            root = new PartInfo("", rootPart, rootParts);
        }

        // Detect all cycles in the parent graph (handles A→A, A→B→A, A→B→C→A, etc.)
        final Map<String, String> parentGraph = new LinkedHashMap<>();
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
                    ViaBedrockUtilityNeoForge.LOGGER.warn(
                            "[GeometryUtil] Detected circular parent chain: {} — breaking cycle by attaching to root",
                            String.join(" → ", cycleMembers) + " → " + current);
                    break;
                }
                path.add(current);
                current = parentGraph.get(current);
            }
            processed.addAll(path);
        }

        for (Map.Entry<String, PartInfo> entry : stringToPart.entrySet()) {
            if (entry.getValue().parent.isBlank() && entry.getValue().part() != root.part) {
                putChild(root, entry.getKey(), entry.getValue().part());
                continue;
            }

            if (cyclicBones.contains(entry.getKey())) {
                putChild(root, entry.getKey(), entry.getValue().part());
                continue;
            }

            // The tree root must not be re-added as a child of any other bone
            // (e.g. Bedrock skins with "world" → "root" hierarchy where "root" is already the tree root)
            if (entry.getValue().part() == root.part()) {
                continue;
            }

            PartInfo parentPart = stringToPart.get(entry.getValue().parent);
            if (parentPart != null) {
                putChild(parentPart, entry.getKey(), entry.getValue().part);
            }
        }

        // Validate the actual ModelPart tree for cycles (identity-based, not name-based)
        validateAndFixCycles(root.part(), "root", new IdentityHashMap<>(), new ArrayList<>(), geometryName);
        freezeTopology(root.part(), new IdentityHashMap<>());

        if (player) {
            PlayerModel model = new PlayerModel(root.part(), slim);
            BedrockPlayerModelMetadata.register(model, playerMetadata);
            return model;
        }
        return new CustomEntityModel<>(root.part());
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

    static String presentationParent(boolean player, String boneName, String semanticParent) {
        if (!player || boneName == null) {
            return semanticParent;
        }
        return switch (boneName.toLowerCase(Locale.ROOT)) {
            case "head", "rightarm", "body", "leftarm", "leftleg", "rightleg" -> "root";
            default -> semanticParent;
        };
    }

    private static Vector3f toJavaPivot(Position3V pivot) {
        return BedrockTransformConvention.toJavaModel(new Vector3f(pivot.getX(), pivot.getY(), pivot.getZ()));
    }

    private static Vector3f toJavaGeometry(Vector3f bedrock, boolean localGeometry) {
        return localGeometry
                ? BedrockTransformConvention.toJavaLocalModel(bedrock)
                : BedrockTransformConvention.toJavaModel(bedrock);
    }

    /** CPU-side alpha snapshot used only while constructing a cached attachable model. */
    public record TextureAlpha(int width, int height, byte[] alpha) {
        public TextureAlpha {
            if (width <= 0 || height <= 0 || alpha == null || alpha.length != width * height) {
                throw new IllegalArgumentException("Invalid texture alpha dimensions");
            }
            alpha = alpha.clone();
        }

        public static TextureAlpha from(BufferedImage image) {
            Objects.requireNonNull(image, "image");
            final int width = image.getWidth();
            final int height = image.getHeight();
            final byte[] alpha = new byte[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    alpha[y * width + x] = (byte) ((image.getRGB(x, y) >>> 24) & 0xff);
                }
            }
            return new TextureAlpha(width, height, alpha);
        }

        boolean opaque(int x, int y) {
            return x >= 0 && x < width && y >= 0 && y < height
                    && (alpha[y * width + x] & 0xff) != 0;
        }

        boolean transparent(int x, int y) {
            return !opaque(x, y);
        }
    }

    private static void appendCuboid(List<CuboidGroup> groups, CuboidTransform transform, ModelPart.Cube cuboid) {
        if (GROUP_CUBOIDS && !groups.isEmpty()) {
            final CuboidGroup last = groups.get(groups.size() - 1);
            if (last.transform.equals(transform)) {
                last.cuboids.add(cuboid);
                return;
            }
        }
        groups.add(new CuboidGroup(transform, cuboid));
    }

    private static void putChild(PartInfo parent, String name, ModelPart child) {
        parent.children.put(name, child);
        ((IModelPart) (Object) parent.part).viaBedrockUtility$invalidateChildrenCache();
    }

    private static void freezeTopology(ModelPart part, IdentityHashMap<ModelPart, Boolean> visited) {
        if (visited.put(part, Boolean.TRUE) != null) {
            return;
        }
        final IModelPart extension = (IModelPart) (Object) part;
        for (ModelPart child : extension.viaBedrockUtility$getChildren().values()) {
            freezeTopology(child, visited);
        }
        extension.viaBedrockUtility$freezeTopology();
    }

    /**
     * DFS validation of the actual ModelPart tree using object identity.
     * Detects and removes cyclic edges that the name-based detection might miss.
     */
    private static void validateAndFixCycles(ModelPart part, String name, IdentityHashMap<ModelPart, String> ancestors, List<String> path, String geometryName) {
        if (ancestors.containsKey(part)) {
            // This ModelPart object is already an ancestor — cycle detected!
            String cyclePath = String.join(" → ", path) + " → " + name + " (CYCLE to '" + ancestors.get(part) + "')";
            ViaBedrockUtilityNeoForge.LOGGER.error(
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
                // Child points to an ancestor — remove this cyclic edge
                String cyclePath = String.join(" → ", path) + " → " + entry.getKey() + " (CYCLE to '" + ancestors.get(entry.getValue()) + "')";
                ViaBedrockUtilityNeoForge.LOGGER.error(
                        "[GeometryUtil] Removing cyclic ModelPart edge in geometry '{}': {}",
                        geometryName != null ? geometryName : "unknown", cyclePath);
                it.remove();
                // Structural change to this part's children map: discard any build-time snapshot before
                // the final explicit topology freeze.
                ((IModelPart) ((Object) part)).viaBedrockUtility$invalidateChildrenCache();
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
        Float[] uvA = map.getUvMap().remove(a);
        Float[] uvB = map.getUvMap().remove(b);
        // Flip U (swap u1↔u2) to compensate for reversed vertex winding on the swapped face.
        // Each face pair (EAST/WEST, NORTH/SOUTH) has opposite vertex ordering along one axis,
        // so placing one face's UV on the other requires a horizontal flip.
        if (uvA != null) map.getUvMap().put(b, new Float[]{uvA[2], uvA[1], uvA[0], uvA[3]});
        if (uvB != null) map.getUvMap().put(a, new Float[]{uvB[2], uvB[1], uvB[0], uvB[3]});
    }

    private static void markAsVbuBox(ModelPart.Cube cuboid, float inflate, boolean mirror) {
        float x0 = cuboid.minX - inflate;
        final float y0 = cuboid.minY - inflate;
        final float z0 = cuboid.minZ - inflate;
        float x1 = cuboid.maxX + inflate;
        final float y1 = cuboid.maxY + inflate;
        final float z1 = cuboid.maxZ + inflate;
        if (mirror) {
            final float swap = x0;
            x0 = x1;
            x1 = swap;
        }
        ((ICuboid) (Object) cuboid).viaBedrockUtility$markAsVBUBox(x0, y0, z0, x1, y1, z1);
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
            Float[] uv = map.getUvMap().get(org.cube.converter.util.element.Direction.UP);
            if (uv == null) uv = map.getUvMap().get(org.cube.converter.util.element.Direction.DOWN);
            // Swap both u1↔u2 and v1↔v2 to compensate for scale(-1,-1,1): X negation flips U,
            // and Y negation flips the viewing side which flips V on horizontal faces
            sides[s++] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex6, vertex5, vertex, vertex2}, uv[2], uv[3], uv[0], uv[1], uvWidth, uvHeight, mirror, Direction.DOWN);
        }

        if (set.contains(Direction.UP)) {
            // Bedrock UP/DOWN texture regions are swapped vs Java; swap UV if both faces exist (box UV)
            Float[] uv = map.getUvMap().get(org.cube.converter.util.element.Direction.DOWN);
            if (uv == null) uv = map.getUvMap().get(org.cube.converter.util.element.Direction.UP);
            // Swap both u1↔u2 and v1↔v2 to compensate for scale(-1,-1,1): X negation flips U,
            // and Y negation flips the viewing side which flips V on horizontal faces
            sides[s++] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex3, vertex4, vertex8, vertex7}, uv[2], uv[3], uv[0], uv[1], uvWidth, uvHeight, mirror, Direction.UP);
        }

        if (set.contains(Direction.WEST)) {
            final Float[] uv = map.getUvMap().get(org.cube.converter.util.element.Direction.WEST);
            // Swap u1/u2 to compensate for horizontal flip caused by global scale(-1,-1,1) Z negation
            sides[s++] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex, vertex5, vertex8, vertex4}, uv[2], uv[1], uv[0], uv[3], uvWidth, uvHeight, mirror, Direction.WEST);
        }

        if (set.contains(Direction.NORTH)) {
            final Float[] uv = CoincidentFaceUv.northUv(
                    map, h - z - inflate * 2F, inflate, mirror, uvWidth, uvHeight);
            sides[s++] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex2, vertex, vertex4, vertex3}, uv[0], uv[1], uv[2], uv[3], uvWidth, uvHeight, mirror, Direction.NORTH);
        }

        if (set.contains(Direction.EAST)) {
            final Float[] uv = map.getUvMap().get(org.cube.converter.util.element.Direction.EAST);
            // Swap u1/u2 to compensate for horizontal flip caused by global scale(-1,-1,1) Z negation
            sides[s++] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex6, vertex2, vertex3, vertex7}, uv[2], uv[1], uv[0], uv[3], uvWidth, uvHeight, mirror, Direction.EAST);
        }

        if (set.contains(Direction.SOUTH)) {
            final Float[] uv = CoincidentFaceUv.southUv(
                    map, h - z - inflate * 2F, inflate, mirror, uvWidth, uvHeight);
            sides[s] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex5, vertex6, vertex7, vertex8}, uv[0], uv[1], uv[2], uv[3], uvWidth, uvHeight, mirror, Direction.SOUTH);
        }
    }

    private static void buildPolyMeshCuboids(PolyMesh polyMesh, float uvWidth, float uvHeight,
                                              List<CuboidGroup> cuboidGroups, boolean localGeometry) {
        final float[][] pmPositions = polyMesh.getPositions();
        final float[][] pmNormals = polyMesh.getNormals();
        final float[][] pmUvs = polyMesh.getUvs();
        final int[][][] pmPolys = polyMesh.getPolys();
        final boolean normalizedUvs = polyMesh.isNormalizedUvs();

        // Build quad data from poly_mesh polygons
        final List<PolyQuadData> allPolyQuads = new ArrayList<>();
        for (int[][] poly : pmPolys) {
            int vertCount = Math.min(poly.length, 4);
            ModelPart.Vertex[] verts = new ModelPart.Vertex[4];

            float avgNx = 0, avgNy = 0, avgNz = 0;
            for (int v = 0; v < vertCount; v++) {
                int posIdx = poly[v][0];
                int normIdx = poly[v][1];
                int uvIdx = poly[v][2];

                float px = pmPositions[posIdx][0];
                float py = pmPositions[posIdx][1];
                float pz = pmPositions[posIdx][2];

                // Coordinate transform: Bedrock -> Java model space (Y inverted + presentation origin)
                final Vector3f javaPos = toJavaGeometry(new Vector3f(px, py, pz), localGeometry);
                px = javaPos.x;
                py = javaPos.y;
                pz = javaPos.z;

                // UV transform
                float u = pmUvs[uvIdx][0];
                float vCoord = pmUvs[uvIdx][1];
                if (normalizedUvs) {
                    // Bedrock poly_mesh normalized UVs use V=0 at bottom, V=1 at top (OpenGL convention).
                    // Java/Minecraft uses V=0 at top, V=1 at bottom. Invert V to correct the mapping.
                    vCoord = 1.0f - vCoord;
                } else {
                    u = u / uvWidth;
                    vCoord = vCoord / uvHeight;
                }

                verts[v] = new ModelPart.Vertex(px, py, pz, u, vCoord);

                avgNx += pmNormals[normIdx][0];
                avgNy += pmNormals[normIdx][1];
                avgNz += pmNormals[normIdx][2];
            }

            // Degenerate triangle to quad
            if (vertCount == 3) {
                verts[3] = verts[2];
            }

            Direction dir = normalToDirection(avgNx / vertCount, avgNy / vertCount, avgNz / vertCount);
            allPolyQuads.add(new PolyQuadData(verts, dir));
        }

        // Group into batches of 6 (max quads per Cuboid's sides array)
        for (int batch = 0; batch < allPolyQuads.size(); batch += 6) {
            int batchEnd = Math.min(batch + 6, allPolyQuads.size());
            int batchSize = batchEnd - batch;

            // Create direction set with exactly batchSize entries to size the sides array
            Set<Direction> dirSet = EnumSet.noneOf(Direction.class);
            for (int d = 0; d < batchSize; d++) {
                dirSet.add(Direction.values()[d]);
            }

            // Create dummy Cuboid — its sides array will be fully replaced
            ModelPart.Cube cuboid = new ModelPart.Cube(
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 1, 1, dirSet);

            // Replace sides with poly_mesh quads
            ModelPart.Polygon[] sides = cuboid.polygons;
            for (int q = 0; q < batchSize; q++) {
                PolyQuadData qd = allPolyQuads.get(batch + q);
                // Create Quad with dummy vertices (constructor will overwrite their UVs)
                ModelPart.Vertex[] dummyVerts = new ModelPart.Vertex[]{
                        new ModelPart.Vertex(0, 0, 0, 0, 0),
                        new ModelPart.Vertex(0, 0, 0, 0, 0),
                        new ModelPart.Vertex(0, 0, 0, 0, 0),
                        new ModelPart.Vertex(0, 0, 0, 0, 0)
                };
                sides[q] = new ModelPart.Polygon(dummyVerts, 0, 0, 1, 1, 1, 1, false, qd.direction);
                // Replace vertices with correct positions and UVs
                ModelPart.Vertex[] quadVerts = sides[q].vertices();
                for (int vi = 0; vi < 4; vi++) {
                    quadVerts[vi] = qd.vertices[vi];
                }
            }

            ((ICuboid) (Object) cuboid).viaBedrockUtility$markAsVBU();
            appendCuboid(cuboidGroups, CuboidTransform.IDENTITY, cuboid);
        }
    }

    /**
     * Adds the alpha-derived extrusion faces used by Blockbench's texture_mesh preview. A
     * texture mesh is a complete rectangular front/back sheet plus only the pixel-boundary
     * side strips; mapping the entire texture onto every side is what produces the visibly
     * compressed sword/bow edges.
     */
    private static void buildTextureMeshBoundaryCuboids(List<TextureMesh> meshes, float uvWidth,
                                                         float uvHeight,
                                                         Function<String, TextureAlpha> textureResolver,
                                                         List<CuboidGroup> cuboidGroups) {
        for (TextureMesh mesh : meshes) {
            final TextureAlpha texture = textureResolver.apply(mesh.getTexture());
            // Bedrock's texture_mesh always has a rectangular front/back sheet. Alpha data is
            // only needed for the optional pixel-boundary extrusion; a missing image must not
            // turn the whole attachable into an empty model (Java's item/generated path has the
            // same rectangular geometry even before seam fixing).
            final int textureWidth = texture == null ? Math.max(1, Math.round(uvWidth)) : texture.width();
            final int textureHeight = texture == null ? Math.max(1, Math.round(uvHeight)) : texture.height();
            final float xScale = uvWidth / textureWidth;
            final float zScale = uvHeight / textureHeight;
            final float depth = mesh.getDepth();
            final List<BoundaryQuad> faces = new ArrayList<>();

            // Bedrock texture_meshes use an X/Z sprite plane with pixel depth along Y. This is
            // also why the vanilla bow definitions use a non-zero local_pivot.z while depth is
            // only one pixel. Java's generated item uses a different local axis, so only the
            // alpha-span generation is shared; the Bedrock axes stay intact here.
            faces.add(new BoundaryQuad(
                    new float[][]{{0, 0, 0}, {uvWidth, 0, 0},
                            {uvWidth, 0, uvHeight}, {0, 0, uvHeight}},
                    new float[]{0, -1, 0},
                    new float[]{0, 0, 1, 0, 1, 1, 0, 1}, mesh));
            faces.add(new BoundaryQuad(
                    new float[][]{{uvWidth, depth, 0}, {0, depth, 0},
                            {0, depth, uvHeight}, {uvWidth, depth, uvHeight}},
                    new float[]{0, 1, 0},
                    new float[]{1, 0, 1, 1, 0, 1, 0, 0}, mesh));

            if (texture != null) {
                for (SpriteSpan span : spriteSpans(texture)) {
                    final float min = span.min();
                    final float max = span.max() + 1.0F;
                    final float anchor = span.anchor();
                    final float[][] positions;
                    final float[] normal;
                    final float[] uvs;
                    switch (span.facing()) {
                        case UP -> {
                            final float z = anchor * zScale;
                            positions = new float[][]{{min * xScale, 0, z}, {max * xScale, 0, z},
                                    {max * xScale, depth, z}, {min * xScale, depth, z}};
                            normal = new float[]{0, 0, -1};
                            uvs = new float[]{min / texture.width(), anchor / texture.height(),
                                    max / texture.width(), anchor / texture.height(),
                                    max / texture.width(), (anchor + 1.0F) / texture.height(),
                                    min / texture.width(), (anchor + 1.0F) / texture.height()};
                        }
                        case DOWN -> {
                            final float z = (anchor + 1.0F) * zScale;
                            positions = new float[][]{{min * xScale, depth, z}, {max * xScale, depth, z},
                                    {max * xScale, 0, z}, {min * xScale, 0, z}};
                            normal = new float[]{0, 0, 1};
                            uvs = new float[]{min / texture.width(), (anchor + 1.0F) / texture.height(),
                                    max / texture.width(), (anchor + 1.0F) / texture.height(),
                                    max / texture.width(), anchor / texture.height(),
                                    min / texture.width(), anchor / texture.height()};
                        }
                        case LEFT -> {
                            final float x = anchor * xScale;
                            positions = new float[][]{{x, 0, min * zScale}, {x, depth, min * zScale},
                                    {x, depth, max * zScale}, {x, 0, max * zScale}};
                            normal = new float[]{-1, 0, 0};
                            uvs = new float[]{anchor / texture.width(), min / texture.height(),
                                    anchor / texture.width(), max / texture.height(),
                                    (anchor + 1.0F) / texture.width(), max / texture.height(),
                                    (anchor + 1.0F) / texture.width(), min / texture.height()};
                        }
                        case RIGHT -> {
                            final float x = (anchor + 1.0F) * xScale;
                            positions = new float[][]{{x, depth, min * zScale}, {x, 0, min * zScale},
                                    {x, 0, max * zScale}, {x, depth, max * zScale}};
                            normal = new float[]{1, 0, 0};
                            uvs = new float[]{(anchor + 1.0F) / texture.width(), max / texture.height(),
                                    (anchor + 1.0F) / texture.width(), min / texture.height(),
                                    anchor / texture.width(), min / texture.height(),
                                    anchor / texture.width(), max / texture.height()};
                        }
                        default -> throw new AssertionError(span.facing());
                    }
                    faces.add(new BoundaryQuad(positions, normal, uvs, mesh));
                }
            }

            for (int batch = 0; batch < faces.size(); batch += 6) {
                final int end = Math.min(batch + 6, faces.size());
                final EnumSet<Direction> directions = EnumSet.noneOf(Direction.class);
                for (int i = 0; i < end - batch; i++) {
                    directions.add(Direction.values()[i]);
                }
                final ModelPart.Cube cuboid = new ModelPart.Cube(
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 1, 1, directions);
                final ModelPart.Polygon[] polygons = cuboid.polygons;
                for (int i = batch; i < end; i++) {
                    final BoundaryQuad face = faces.get(i);
                    final ModelPart.Vertex[] vertices = new ModelPart.Vertex[4];
                    for (int v = 0; v < 4; v++) {
                        final float[] p = transformTextureMeshPoint(face.positions()[v], face.mesh());
                        final float[] uv = face.uvs();
                        vertices[v] = new ModelPart.Vertex(
                                toJavaGeometry(new Vector3f(p[0], p[1], p[2]), true),
                                uv[v * 2], uv[v * 2 + 1]);
                    }
                    final float[] n = transformTextureMeshNormal(face.normal(), face.mesh());
                    polygons[i - batch] = new ModelPart.Polygon(
                            vertices, BedrockTransformConvention.toJavaNormal(new Vector3f(n[0], n[1], n[2])));
                }
                ((ICuboid) (Object) cuboid).viaBedrockUtility$markAsVBU();
                appendCuboid(cuboidGroups, CuboidTransform.IDENTITY, cuboid);
            }
        }
    }

    private static List<SpriteSpan> spriteSpans(TextureAlpha texture) {
        final List<SpriteSpan> spans = new ArrayList<>();
        for (int y = 0; y < texture.height(); y++) {
            for (int x = 0; x < texture.width(); x++) {
                final boolean visible = !texture.transparent(x, y);
                checkSpriteTransition(SpriteFacing.UP, spans, texture, x, y, visible);
                checkSpriteTransition(SpriteFacing.DOWN, spans, texture, x, y, visible);
                checkSpriteTransition(SpriteFacing.LEFT, spans, texture, x, y, visible);
                checkSpriteTransition(SpriteFacing.RIGHT, spans, texture, x, y, visible);
            }
        }
        return spans;
    }

    private static void checkSpriteTransition(SpriteFacing facing, List<SpriteSpan> spans,
                                              TextureAlpha texture, int x, int y, boolean visible) {
        if (texture.transparent(x + facing.xOffset, y + facing.yOffset) && visible) {
            final int anchor = facing.horizontal() ? y : x;
            final int position = facing.horizontal() ? x : y;
            SpriteSpan existing = null;
            for (SpriteSpan span : spans) {
                if (span.facing() == facing && span.anchor() == anchor) {
                    existing = span;
                    break;
                }
            }
            if (existing == null) {
                spans.add(new SpriteSpan(facing, position, position, anchor));
            } else {
                existing.expand(position);
            }
        }
    }

    private static float[] transformTextureMeshPoint(float[] point, TextureMesh mesh) {
        final Position3V pivot = mesh.getLocalPivot();
        final Position3V scale = mesh.getScale();
        final Position3V rotation = mesh.getRotation();
        final Position3V position = mesh.getPosition();
        float x = (point[0] - pivot.getX()) * scale.getX();
        float y = (point[1] - pivot.getY()) * scale.getY();
        float z = (point[2] - pivot.getZ()) * scale.getZ();
        final Vector3f rotated = rotateTextureMesh(new Vector3f(x, y, z), rotation);
        return new float[]{rotated.x + position.getX(), rotated.y + position.getY(), rotated.z + position.getZ()};
    }

    private static float[] transformTextureMeshNormal(float[] normal, TextureMesh mesh) {
        final Vector3f rotated = rotateTextureMesh(new Vector3f(normal[0], normal[1], normal[2]), mesh.getRotation());
        return new float[]{rotated.x, rotated.y, rotated.z};
    }

    private static Vector3f rotateTextureMesh(Vector3f value, Position3V rotation) {
        return new Matrix3f().rotationZYX((float) Math.toRadians(rotation.getZ()),
                (float) Math.toRadians(rotation.getY()), (float) Math.toRadians(rotation.getX())).transform(value);
    }

    private record BoundaryQuad(float[][] positions, float[] normal, float[] uvs, TextureMesh mesh) {
    }

    private enum SpriteFacing {
        UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

        private final int xOffset;
        private final int yOffset;

        SpriteFacing(int xOffset, int yOffset) {
            this.xOffset = xOffset;
            this.yOffset = yOffset;
        }

        boolean horizontal() {
            return this == UP || this == DOWN;
        }
    }

    private static final class SpriteSpan {
        private final SpriteFacing facing;
        private int min;
        private int max;
        private final int anchor;

        private SpriteSpan(SpriteFacing facing, int min, int max, int anchor) {
            this.facing = facing;
            this.min = min;
            this.max = max;
            this.anchor = anchor;
        }

        private void expand(int position) {
            min = Math.min(min, position);
            max = Math.max(max, position);
        }

        private SpriteFacing facing() { return facing; }
        private int min() { return min; }
        private int max() { return max; }
        private int anchor() { return anchor; }
        private boolean horizontal() { return facing.horizontal(); }
    }

    private static Direction normalToDirection(float nx, float ny, float nz) {
        final Vector3f java = BedrockTransformConvention.toJavaNormal(new Vector3f(nx, ny, nz));
        nx = java.x;
        ny = java.y;
        nz = java.z;
        float absX = Math.abs(nx), absY = Math.abs(ny), absZ = Math.abs(nz);
        if (absY >= absX && absY >= absZ) {
            return ny > 0 ? Direction.UP : Direction.DOWN;
        } else if (absX >= absZ) {
            return nx > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return nz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private record CuboidTransform(int pivotX, int pivotY, int pivotZ,
                                   int rotationX, int rotationY, int rotationZ) {
        private static final CuboidTransform IDENTITY = new CuboidTransform(0, 0, 0, 0, 0, 0);

        static CuboidTransform from(Cube cube, boolean localGeometry) {
            final Position3V rotation = cube.getRotation();
            if (rotation.getX() == 0.0F && rotation.getY() == 0.0F && rotation.getZ() == 0.0F) {
                // With no rotation the pivot translations cancel, so all such cubes share one transform.
                return IDENTITY;
            }
            final Position3V pivot = cube.getPivot();
            return new CuboidTransform(
                    Float.floatToRawIntBits(pivot.getX()),
                    Float.floatToRawIntBits(pivot.getY()),
                    Float.floatToRawIntBits(pivot.getZ()),
                    Float.floatToRawIntBits(rotation.getX()),
                    Float.floatToRawIntBits(rotation.getY()),
                    Float.floatToRawIntBits(rotation.getZ())
            );
        }

        Vector3f javaPivot(boolean localGeometry) {
            if (this == IDENTITY) {
                return new Vector3f();
            }
            Vector3f pivot = new Vector3f(
                    Float.intBitsToFloat(this.pivotX),
                    Float.intBitsToFloat(this.pivotY),
                    Float.intBitsToFloat(this.pivotZ));
            return toJavaGeometry(pivot, localGeometry);
        }

        Vector3f rotation() {
            if (this == IDENTITY) {
                return new Vector3f();
            }
            return new Vector3f(
                    Float.intBitsToFloat(this.rotationX),
                    Float.intBitsToFloat(this.rotationY),
                    Float.intBitsToFloat(this.rotationZ)
            );
        }
    }

    private static final class CuboidGroup {
        final CuboidTransform transform;
        final List<ModelPart.Cube> cuboids = new ArrayList<>();

        CuboidGroup(CuboidTransform transform, ModelPart.Cube first) {
            this.transform = transform;
            this.cuboids.add(first);
        }
    }

    private record PolyQuadData(ModelPart.Vertex[] vertices, Direction direction) {}

}
