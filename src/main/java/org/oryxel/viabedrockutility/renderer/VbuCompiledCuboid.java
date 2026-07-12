package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;

/** Immutable final topology for a VBU cuboid, plus a small cache for its animated V offset. */
public final class VbuCompiledCuboid {
    public static final int ENTITY_VERTEX_STRIDE = 36;

    private static final float MODEL_SCALE = 1.0F / 16.0F;

    private final boolean box;
    private final int vertexCount;
    private final int positionCount;
    private final int[] faceOffsets;
    private final int[] vertexPositionIndices;
    private final byte[] faceDirections;
    private final float[] faceNormals;
    private final long[] baseUvs;
    private final float[] baseV;

    private final float originX;
    private final float originY;
    private final float originZ;
    private final float sizeX;
    private final float sizeY;
    private final float sizeZ;
    private final float[] genericPositions;

    private int resolvedVOffsetBits;
    private long[] resolvedUvs;
    private long[] shiftedUvs;

    private VbuCompiledCuboid(boolean box,
                              int vertexCount,
                              int positionCount,
                              int[] faceOffsets,
                              int[] vertexPositionIndices,
                              byte[] faceDirections,
                              float[] faceNormals,
                              long[] baseUvs,
                              float[] baseV,
                              float originX,
                              float originY,
                              float originZ,
                              float sizeX,
                              float sizeY,
                              float sizeZ,
                              float[] genericPositions) {
        this.box = box;
        this.vertexCount = vertexCount;
        this.positionCount = positionCount;
        this.faceOffsets = faceOffsets;
        this.vertexPositionIndices = vertexPositionIndices;
        this.faceDirections = faceDirections;
        this.faceNormals = faceNormals;
        this.baseUvs = baseUvs;
        this.baseV = baseV;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.genericPositions = genericPositions;
        this.resolvedUvs = baseUvs;
    }

    public static VbuCompiledCuboid compile(ModelPart.Polygon[] polygons) {
        return compile(polygons, null);
    }

    /** Compiles a box using its complete post-inflate endpoints, even when some faces are omitted. */
    public static VbuCompiledCuboid compileBox(ModelPart.Polygon[] polygons,
                                                float x0, float y0, float z0,
                                                float x1, float y1, float z1) {
        return compile(polygons, new BoxCoordinates(
                x0 * MODEL_SCALE, y0 * MODEL_SCALE, z0 * MODEL_SCALE,
                x1 * MODEL_SCALE, y1 * MODEL_SCALE, z1 * MODEL_SCALE));
    }

    private static VbuCompiledCuboid compile(ModelPart.Polygon[] polygons, BoxCoordinates boxCoordinates) {
        int faceCount = 0;
        int vertexCount = 0;
        for (ModelPart.Polygon polygon : polygons) {
            if (polygon != null) {
                faceCount++;
                vertexCount = Math.addExact(vertexCount, polygon.vertices().length);
            }
        }

        final int[] faceOffsets = new int[faceCount + 1];
        final float[] flattenedPositions = new float[vertexCount * 3];
        final long[] baseUvs = new long[vertexCount];
        final float[] baseV = new float[vertexCount];
        final byte[] faceDirections = new byte[faceCount];
        final float[] faceNormals = new float[faceCount * 3];

        int faceIndex = 0;
        int vertexIndex = 0;
        for (ModelPart.Polygon polygon : polygons) {
            if (polygon == null) {
                continue;
            }

            faceOffsets[faceIndex] = vertexIndex;
            final Vector3f normal = polygon.normal();
            faceDirections[faceIndex] = directionIndex(normal);
            faceNormals[faceIndex * 3] = normal.x();
            faceNormals[faceIndex * 3 + 1] = normal.y();
            faceNormals[faceIndex * 3 + 2] = normal.z();

            for (ModelPart.Vertex vertex : polygon.vertices()) {
                final Vector3f position = vertex.pos();
                final int positionOffset = vertexIndex * 3;
                flattenedPositions[positionOffset] = position.x() * MODEL_SCALE;
                flattenedPositions[positionOffset + 1] = position.y() * MODEL_SCALE;
                flattenedPositions[positionOffset + 2] = position.z() * MODEL_SCALE;
                baseUvs[vertexIndex] = VbuCompileScratch.packFloats(vertex.u(), vertex.v());
                baseV[vertexIndex] = vertex.v();
                vertexIndex++;
            }
            faceIndex++;
        }
        faceOffsets[faceCount] = vertexCount;

        if (boxCoordinates != null) {
            final int[] positionIndices = mapBoxPositions(flattenedPositions, vertexCount, boxCoordinates);
            if (positionIndices != null) {
                return createBox(vertexCount, faceOffsets, positionIndices, faceDirections, faceNormals,
                        baseUvs, baseV, boxCoordinates);
            }
        }

        final AxisValues xValues = new AxisValues();
        final AxisValues yValues = new AxisValues();
        final AxisValues zValues = new AxisValues();
        boolean box = true;
        for (int index = 0; index < vertexCount; index++) {
            final int offset = index * 3;
            box &= xValues.add(flattenedPositions[offset]);
            box &= yValues.add(flattenedPositions[offset + 1]);
            box &= zValues.add(flattenedPositions[offset + 2]);
        }

        if (box) {
            xValues.normalizeOrder();
            yValues.normalizeOrder();
            zValues.normalizeOrder();
            final int[] positionIndices = new int[vertexCount];
            for (int index = 0; index < vertexCount; index++) {
                final int offset = index * 3;
                positionIndices[index] = xValues.indexOf(flattenedPositions[offset])
                        | (yValues.indexOf(flattenedPositions[offset + 1]) << 1)
                        | (zValues.indexOf(flattenedPositions[offset + 2]) << 2);
            }

            return createBox(vertexCount, faceOffsets, positionIndices, faceDirections, faceNormals,
                    baseUvs, baseV, new BoxCoordinates(
                            xValues.firstOrZero(), yValues.firstOrZero(), zValues.firstOrZero(),
                            xValues.secondOrFirst(), yValues.secondOrFirst(), zValues.secondOrFirst()));
        }

        final int[] positionIndices = new int[vertexCount];
        final float[] uniquePositions = new float[flattenedPositions.length];
        int positionCount = 0;
        for (int index = 0; index < vertexCount; index++) {
            final int sourceOffset = index * 3;
            int uniqueIndex = findPosition(uniquePositions, positionCount,
                    flattenedPositions[sourceOffset],
                    flattenedPositions[sourceOffset + 1],
                    flattenedPositions[sourceOffset + 2]);
            if (uniqueIndex < 0) {
                uniqueIndex = positionCount++;
                final int targetOffset = uniqueIndex * 3;
                uniquePositions[targetOffset] = flattenedPositions[sourceOffset];
                uniquePositions[targetOffset + 1] = flattenedPositions[sourceOffset + 1];
                uniquePositions[targetOffset + 2] = flattenedPositions[sourceOffset + 2];
            }
            positionIndices[index] = uniqueIndex;
        }

        return new VbuCompiledCuboid(
                false, vertexCount, positionCount, faceOffsets, positionIndices, faceDirections, faceNormals,
                baseUvs, baseV,
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                Arrays.copyOf(uniquePositions, positionCount * 3));
    }

    public int vertexCount() {
        return this.vertexCount;
    }

    public boolean isBox() {
        return this.box;
    }

    /** Writes a complete NEW_ENTITY vertex stream and returns the number of emitted vertices. */
    public int writeVertices(PoseStack.Pose pose,
                             long pointer,
                             int stride,
                             int colorAbgr,
                             int overlay,
                             int light,
                             float vOffset,
                             boolean flatNormal) {
        if (stride != ENTITY_VERTEX_STRIDE) {
            throw new IllegalArgumentException("Unsupported entity vertex stride: " + stride);
        }

        VbuCompileScratch.ensurePositionCapacity(this.positionCount);
        if (this.box) {
            this.prepareBoxPositions(pose.pose(), colorAbgr);
        } else {
            this.prepareGenericPositions(pose.pose(), colorAbgr);
        }

        if (!flatNormal) {
            VbuCompileScratch.prepareDirectionNormals(pose);
        }

        final long[] uvs = this.resolveUvs(vOffset);
        final long packedOverlayLight = VbuCompileScratch.packInts(overlay, light);
        long destination = pointer;

        for (int face = 0; face < this.faceDirections.length; face++) {
            final int packedNormal;
            if (flatNormal) {
                packedNormal = VbuCompileScratch.FLAT_PACKED_NORMAL;
            } else {
                final int direction = this.faceDirections[face];
                if (direction >= 0) {
                    packedNormal = VbuCompileScratch.directionNormal(direction);
                } else {
                    final int normalOffset = face * 3;
                    packedNormal = VbuCompileScratch.transformNormal(
                            pose,
                            this.faceNormals[normalOffset],
                            this.faceNormals[normalOffset + 1],
                            this.faceNormals[normalOffset + 2]);
                }
            }

            final int end = this.faceOffsets[face + 1];
            for (int vertex = this.faceOffsets[face]; vertex < end; vertex++) {
                final int position = this.vertexPositionIndices[vertex];
                MemoryUtil.memPutLong(destination, VbuCompileScratch.transformedXY(position));
                MemoryUtil.memPutLong(destination + 8L, VbuCompileScratch.transformedZColor(position));
                MemoryUtil.memPutLong(destination + 16L, uvs[vertex]);
                MemoryUtil.memPutLong(destination + 24L, packedOverlayLight);
                MemoryUtil.memPutInt(destination + 32L, packedNormal);
                destination += stride;
            }
        }

        return this.vertexCount;
    }

    private void prepareBoxPositions(Matrix4f pose, int colorAbgr) {
        final float vxx = pose.m00() * this.sizeX;
        final float vxy = pose.m01() * this.sizeX;
        final float vxz = pose.m02() * this.sizeX;
        final float vyx = pose.m10() * this.sizeY;
        final float vyy = pose.m11() * this.sizeY;
        final float vyz = pose.m12() * this.sizeY;
        final float vzx = pose.m20() * this.sizeZ;
        final float vzy = pose.m21() * this.sizeZ;
        final float vzz = pose.m22() * this.sizeZ;

        final float c000x = transformPositionX(pose, this.originX, this.originY, this.originZ);
        final float c000y = transformPositionY(pose, this.originX, this.originY, this.originZ);
        final float c000z = transformPositionZ(pose, this.originX, this.originY, this.originZ);
        VbuCompileScratch.setPosition(0, c000x, c000y, c000z, colorAbgr);

        final float c100x = c000x + vxx;
        final float c100y = c000y + vxy;
        final float c100z = c000z + vxz;
        VbuCompileScratch.setPosition(1, c100x, c100y, c100z, colorAbgr);

        final float c110x = c100x + vyx;
        final float c110y = c100y + vyy;
        final float c110z = c100z + vyz;
        VbuCompileScratch.setPosition(3, c110x, c110y, c110z, colorAbgr);

        final float c010x = c000x + vyx;
        final float c010y = c000y + vyy;
        final float c010z = c000z + vyz;
        VbuCompileScratch.setPosition(2, c010x, c010y, c010z, colorAbgr);

        final float c001x = c000x + vzx;
        final float c001y = c000y + vzy;
        final float c001z = c000z + vzz;
        VbuCompileScratch.setPosition(4, c001x, c001y, c001z, colorAbgr);

        VbuCompileScratch.setPosition(5, c100x + vzx, c100y + vzy, c100z + vzz, colorAbgr);
        VbuCompileScratch.setPosition(7, c110x + vzx, c110y + vzy, c110z + vzz, colorAbgr);
        VbuCompileScratch.setPosition(6, c010x + vzx, c010y + vzy, c010z + vzz, colorAbgr);
    }

    private void prepareGenericPositions(Matrix4f pose, int colorAbgr) {
        for (int position = 0; position < this.positionCount; position++) {
            final int offset = position * 3;
            final float x = this.genericPositions[offset];
            final float y = this.genericPositions[offset + 1];
            final float z = this.genericPositions[offset + 2];
            VbuCompileScratch.setPosition(
                    position,
                    transformPositionX(pose, x, y, z),
                    transformPositionY(pose, x, y, z),
                    transformPositionZ(pose, x, y, z),
                    colorAbgr);
        }
    }

    private long[] resolveUvs(float vOffset) {
        final int offsetBits = Float.floatToRawIntBits(vOffset);
        if (offsetBits == this.resolvedVOffsetBits) {
            return this.resolvedUvs;
        }

        if (offsetBits == 0) {
            this.resolvedUvs = this.baseUvs;
        } else {
            if (this.shiftedUvs == null) {
                this.shiftedUvs = new long[this.baseUvs.length];
            }
            for (int vertex = 0; vertex < this.baseUvs.length; vertex++) {
                final float u = Float.intBitsToFloat(VbuCompileScratch.unpackFirstInt(this.baseUvs[vertex]));
                this.shiftedUvs[vertex] = VbuCompileScratch.packFloats(u, this.baseV[vertex] + vOffset);
            }
            this.resolvedUvs = this.shiftedUvs;
        }

        this.resolvedVOffsetBits = offsetBits;
        return this.resolvedUvs;
    }

    private static VbuCompiledCuboid createBox(int vertexCount,
                                                int[] faceOffsets,
                                                int[] positionIndices,
                                                byte[] faceDirections,
                                                float[] faceNormals,
                                                long[] baseUvs,
                                                float[] baseV,
                                                BoxCoordinates box) {
        return new VbuCompiledCuboid(
                true, vertexCount, 8, faceOffsets, positionIndices, faceDirections, faceNormals,
                baseUvs, baseV,
                box.x0, box.y0, box.z0,
                box.x1 - box.x0, box.y1 - box.y0, box.z1 - box.z0,
                null);
    }

    private static int[] mapBoxPositions(float[] positions, int vertexCount, BoxCoordinates box) {
        final int[] indices = new int[vertexCount];
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            final int offset = vertex * 3;
            final int x = axisIndex(positions[offset], box.x0, box.x1);
            final int y = axisIndex(positions[offset + 1], box.y0, box.y1);
            final int z = axisIndex(positions[offset + 2], box.z0, box.z1);
            if (x < 0 || y < 0 || z < 0) {
                return null;
            }
            indices[vertex] = x | (y << 1) | (z << 2);
        }
        return indices;
    }

    private static int axisIndex(float value, float first, float second) {
        final int bits = Float.floatToRawIntBits(value);
        if (bits == Float.floatToRawIntBits(first)) {
            return 0;
        }
        if (bits == Float.floatToRawIntBits(second)) {
            return 1;
        }
        return -1;
    }

    private static int findPosition(float[] positions, int positionCount, float x, float y, float z) {
        final int xBits = Float.floatToRawIntBits(x);
        final int yBits = Float.floatToRawIntBits(y);
        final int zBits = Float.floatToRawIntBits(z);
        for (int position = 0; position < positionCount; position++) {
            final int offset = position * 3;
            if (Float.floatToRawIntBits(positions[offset]) == xBits
                    && Float.floatToRawIntBits(positions[offset + 1]) == yBits
                    && Float.floatToRawIntBits(positions[offset + 2]) == zBits) {
                return position;
            }
        }
        return -1;
    }

    private static byte directionIndex(Vector3f normal) {
        for (Direction direction : Direction.values()) {
            final Vector3f step = direction.step();
            if (normal.x() == step.x() && normal.y() == step.y() && normal.z() == step.z()) {
                return (byte) direction.ordinal();
            }
        }
        return -1;
    }

    private static float transformPositionX(Matrix4f pose, float x, float y, float z) {
        return (pose.m00() * x) + ((pose.m10() * y) + ((pose.m20() * z) + pose.m30()));
    }

    private static float transformPositionY(Matrix4f pose, float x, float y, float z) {
        return (pose.m01() * x) + ((pose.m11() * y) + ((pose.m21() * z) + pose.m31()));
    }

    private static float transformPositionZ(Matrix4f pose, float x, float y, float z) {
        return (pose.m02() * x) + ((pose.m12() * y) + ((pose.m22() * z) + pose.m32()));
    }

    private static final class AxisValues {
        private final float[] values = new float[2];
        private int count;

        boolean add(float value) {
            if (!Float.isFinite(value)) {
                return false;
            }
            if (this.indexOf(value) >= 0) {
                return true;
            }
            if (this.count == this.values.length) {
                return false;
            }
            this.values[this.count++] = value;
            return true;
        }

        int indexOf(float value) {
            final int bits = Float.floatToRawIntBits(value);
            for (int index = 0; index < this.count; index++) {
                if (Float.floatToRawIntBits(this.values[index]) == bits) {
                    return index;
                }
            }
            return -1;
        }

        float firstOrZero() {
            return this.count > 0 ? this.values[0] : 0.0F;
        }

        float secondOrFirst() {
            return this.count > 1 ? this.values[1] : this.firstOrZero();
        }

        void normalizeOrder() {
            if (this.count == 2 && Float.compare(this.values[0], this.values[1]) > 0) {
                final float first = this.values[0];
                this.values[0] = this.values[1];
                this.values[1] = first;
            }
        }
    }

    private record BoxCoordinates(float x0, float y0, float z0,
                                  float x1, float y1, float z1) {
    }
}
