package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Render-thread scratch state shared by VBU compiled cuboids. */
public final class VbuCompileScratch {
    public static final int FLAT_PACKED_NORMAL = 0x00007F00;

    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    private static final Direction[] DIRECTIONS = Direction.values();

    private static long[] transformedXY = new long[24];
    private static long[] transformedZColor = new long[24];

    private static final Matrix3f previousNormalMatrix = new Matrix3f();
    private static final int[] transformedDirectionNormals = new int[DIRECTIONS.length];
    private static final Vector3f temporaryNormal = new Vector3f();
    private static boolean normalCacheValid;

    private static ByteBuffer pushBuffer;
    private static boolean pushBufferInUse;

    // Set around emissive model rendering; the render path is single-threaded.
    public static boolean FLAT_NORMAL;

    private VbuCompileScratch() {
    }

    public static Object tryWriter(VertexConsumer consumer) {
        return SodiumPushBackend.tryOf(consumer);
    }

    public static boolean tryBeginPush() {
        if (pushBufferInUse) {
            return false;
        }
        pushBufferInUse = true;
        return true;
    }

    public static void endPush() {
        pushBufferInUse = false;
    }

    public static long acquirePushBuffer(int vertexCount, int stride) {
        if (!pushBufferInUse) {
            throw new IllegalStateException("VBU push buffer acquired outside its guarded scope");
        }
        if (vertexCount < 0 || stride <= 0) {
            throw new IllegalArgumentException("Invalid vertex buffer dimensions");
        }

        final int required = Math.multiplyExact(vertexCount, stride);
        if (pushBuffer == null) {
            pushBuffer = MemoryUtil.memAlloc(growCapacity(required));
        } else if (pushBuffer.capacity() < required) {
            pushBuffer = MemoryUtil.memRealloc(pushBuffer, growCapacity(required));
        }
        return MemoryUtil.memAddress(pushBuffer);
    }

    static void ensurePositionCapacity(int positionCount) {
        if (transformedXY.length >= positionCount) {
            return;
        }

        final int capacity = growCapacity(positionCount);
        transformedXY = new long[capacity];
        transformedZColor = new long[capacity];
    }

    static void setPosition(int index, float x, float y, float z, int colorAbgr) {
        transformedXY[index] = packInts(Float.floatToRawIntBits(x), Float.floatToRawIntBits(y));
        transformedZColor[index] = packInts(Float.floatToRawIntBits(z), colorAbgr);
    }

    static long transformedXY(int index) {
        return transformedXY[index];
    }

    static long transformedZColor(int index) {
        return transformedZColor[index];
    }

    static void prepareDirectionNormals(PoseStack.Pose pose) {
        if (normalCacheValid && pose.normal().equals(previousNormalMatrix)) {
            return;
        }

        for (Direction direction : DIRECTIONS) {
            final Vector3f normal = pose.transformNormal(
                    direction.getStepX(), direction.getStepY(), direction.getStepZ(), temporaryNormal);
            transformedDirectionNormals[direction.ordinal()] = packNormal(normal.x(), normal.y(), normal.z());
        }

        previousNormalMatrix.set(pose.normal());
        normalCacheValid = true;
    }

    static int directionNormal(int directionOrdinal) {
        return transformedDirectionNormals[directionOrdinal];
    }

    static int transformNormal(PoseStack.Pose pose, float x, float y, float z) {
        final Vector3f normal = pose.transformNormal(x, y, z, temporaryNormal);
        return packNormal(normal.x(), normal.y(), normal.z());
    }

    static int packNormal(float x, float y, float z) {
        return normalByte(x) | (normalByte(y) << 8) | (normalByte(z) << 16);
    }

    static long packFloats(float first, float second) {
        return packInts(Float.floatToRawIntBits(first), Float.floatToRawIntBits(second));
    }

    static int unpackFirstInt(long packed) {
        return LITTLE_ENDIAN ? (int) packed : (int) (packed >>> 32);
    }

    static long packInts(int first, int second) {
        if (LITTLE_ENDIAN) {
            return (first & 0xFFFFFFFFL) | ((second & 0xFFFFFFFFL) << 32);
        }
        return ((first & 0xFFFFFFFFL) << 32) | (second & 0xFFFFFFFFL);
    }

    private static int normalByte(float value) {
        return (byte) (Mth.clamp(value, -1.0F, 1.0F) * 127.0F) & 0xFF;
    }

    private static int growCapacity(int required) {
        if (required <= 0) {
            return 1;
        }

        int capacity = 1;
        while (capacity < required && capacity > 0) {
            capacity <<= 1;
        }
        return capacity > 0 ? capacity : required;
    }
}
