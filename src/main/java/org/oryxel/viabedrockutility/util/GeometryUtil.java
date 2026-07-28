package org.oryxel.viabedrockutility.util;

import net.minecraft.client.model.*;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.core.Direction;
import org.cube.converter.model.element.Cube;
import org.cube.converter.model.element.Parent;
import org.cube.converter.model.element.PolyMesh;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;
import org.cube.converter.util.element.Position3V;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import org.oryxel.viabedrockutility.mixin.interfaces.ICuboid;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;
import org.oryxel.viabedrockutility.renderer.BedrockPlayerModelMetadata;
import org.oryxel.viabedrockutility.renderer.model.CustomEntityModel;

import java.util.*;

public final class GeometryUtil {
    // One kill switch restores the pre-Stage-2 one-cuboid-per-part topology and vanilla tree walk.
    private static final boolean GROUP_CUBOIDS =
            !Boolean.getBoolean("viabedrockutility.disableIndexedRender");

    public static Model buildModel(final BedrockGeometryModel geometry, final boolean player, boolean slim) {
        return buildModel(geometry, player, slim, null);
    }

    public static Model buildModel(final BedrockGeometryModel geometry, final boolean player, boolean slim, final String geometryName) {
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

                final Map<Direction, Float[]> javaFaces = BedrockCubeFaceMapping.toJavaFaces(cube.getUvMap().getUvMap());
                final Set<Direction> set = javaFaces.keySet();

                final ModelPart.Cube cuboid = new ModelPart.Cube(0, 0, pos.getX(), -(pos.getY() - 24.016F + sizeY), pos.getZ(), sizeX, sizeY, sizeZ, inflate, inflate, inflate, cube.isMirror(), uvWidth, uvHeight, set);
                correctUv(cuboid, javaFaces, uvWidth, uvHeight, cube.getInflate(), cube.isMirror());
                markAsVbuBox(cuboid, inflate, cube.isMirror());
                appendCuboid(cuboidGroups, CuboidTransform.from(cube), cuboid);
            }

            // poly_mesh vertices are already absolute and use the identity transform. Appending them after
            // boxes preserves their previous relative submission position; an adjacent identity box group
            // may absorb them without changing order.
            if (bone.getPolyMesh() != null) {
                buildPolyMeshCuboids(bone.getPolyMesh(), uvWidth, uvHeight, cuboidGroups);
            }

            int groupIndex = 0;
            for (CuboidGroup group : cuboidGroups) {
                final ModelPart cubePart = new ModelPart(List.copyOf(group.cuboids), Map.of());
                final IModelPart cubePartExtension = (IModelPart) (Object) cubePart;
                cubePartExtension.viaBedrockUtility$setPivot(group.transform.javaPivot());
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

            // All bones (player and entity, including legs) use one coordinate convention: the bone's
            // rotation pivot is its Bedrock pivot mapped to Java space (Y inverted, +24.016 offset), and
            // its cubes are positioned in that same inverted-Y space. No setPos is used.
            partExtension.viaBedrockUtility$setPivot(toJavaPivot(bone.getPivot()));

            String parent = bone.getParent();
            String name = bone.getName();
            if (player) {
                switch (name.toLowerCase(Locale.ROOT)) { // Also do this with the overlays? Those are final, though.
                    case "head", "rightarm", "body", "leftarm", "leftleg", "rightleg" -> parent = "root";
                }
            }

            String adjustedName = adjustFormatting(player, name);
            String adjustedParent = adjustFormatting(player, parent);
            if (playerMetadata != null) {
                playerMetadata.addBone(bone, adjustedName, adjustedParent, part);
            }
            stringToPart.put(adjustedName, new PartInfo(adjustedParent, part, children));
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

    private static Vector3f toJavaPivot(Position3V pivot) {
        return new Vector3f(pivot.getX(), -pivot.getY() + 24.016F, pivot.getZ());
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

    static void correctUv(final ModelPart.Cube cuboid, final Map<Direction, Float[]> faces,
                          final float uvWidth, final float uvHeight, final float inflate,
                          final boolean mirror) {
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

        if (faces.containsKey(Direction.DOWN)) {
            Float[] uv = faces.get(Direction.DOWN);
            // 同时反转 U/V，以补偿实体根变换对水平面的 X/Y 翻转。
            sides[s++] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex6, vertex5, vertex, vertex2}, uv[2], uv[3], uv[0], uv[1], uvWidth, uvHeight, mirror, Direction.DOWN);
        }

        if (faces.containsKey(Direction.UP)) {
            Float[] uv = faces.get(Direction.UP);
            // 同时反转 U/V，以补偿实体根变换对水平面的 X/Y 翻转。
            sides[s++] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex3, vertex4, vertex8, vertex7}, uv[2], uv[3], uv[0], uv[1], uvWidth, uvHeight, mirror, Direction.UP);
        }

        if (faces.containsKey(Direction.WEST)) {
            final Float[] uv = faces.get(Direction.WEST);
            sides[s++] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex, vertex5, vertex8, vertex4}, uv[0], uv[1], uv[2], uv[3], uvWidth, uvHeight, mirror, Direction.WEST);
        }

        if (faces.containsKey(Direction.NORTH)) {
            final Float[] uv = faces.get(Direction.NORTH);
            sides[s++] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex2, vertex, vertex4, vertex3}, uv[0], uv[1], uv[2], uv[3], uvWidth, uvHeight, mirror, Direction.NORTH);
        }

        if (faces.containsKey(Direction.EAST)) {
            final Float[] uv = faces.get(Direction.EAST);
            sides[s++] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex6, vertex2, vertex3, vertex7}, uv[0], uv[1], uv[2], uv[3], uvWidth, uvHeight, mirror, Direction.EAST);
        }

        if (faces.containsKey(Direction.SOUTH)) {
            final Float[] uv = faces.get(Direction.SOUTH);
            sides[s] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex5, vertex6, vertex7, vertex8}, uv[0], uv[1], uv[2], uv[3], uvWidth, uvHeight, mirror, Direction.SOUTH);
        }
    }

    private static void buildPolyMeshCuboids(PolyMesh polyMesh, float uvWidth, float uvHeight,
                                              List<CuboidGroup> cuboidGroups) {
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

                // Coordinate transform: Bedrock -> Java model space (Y inverted + 24.016 offset)
                py = -py + 24.016F;

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

    private static Direction normalToDirection(float nx, float ny, float nz) {
        ny = -ny; // Y axis is inverted (Bedrock -> Java) for all bones
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

        static CuboidTransform from(Cube cube) {
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

        Vector3f javaPivot() {
            if (this == IDENTITY) {
                return new Vector3f();
            }
            return new Vector3f(
                    Float.intBitsToFloat(this.pivotX),
                    -Float.intBitsToFloat(this.pivotY) + 24.016F,
                    Float.intBitsToFloat(this.pivotZ)
            );
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
