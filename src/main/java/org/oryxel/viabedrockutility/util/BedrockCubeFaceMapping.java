package org.oryxel.viabedrockutility.util;

import net.minecraft.core.Direction;

import java.util.EnumMap;
import java.util.Map;

final class BedrockCubeFaceMapping {
    private BedrockCubeFaceMapping() {
    }

    static Map<Direction, Float[]> toJavaFaces(
            Map<org.cube.converter.util.element.Direction, Float[]> source) {
        Map<Direction, Float[]> result = new EnumMap<>(Direction.class);
        // 渲染根节点会反转 X/Y 轴，因此这里只做固定坐标系映射，不能再根据 cube 尺寸换面。
        put(result, Direction.DOWN, source, org.cube.converter.util.element.Direction.UP);
        put(result, Direction.UP, source, org.cube.converter.util.element.Direction.DOWN);
        put(result, Direction.WEST, source, org.cube.converter.util.element.Direction.EAST);
        put(result, Direction.NORTH, source, org.cube.converter.util.element.Direction.NORTH);
        put(result, Direction.EAST, source, org.cube.converter.util.element.Direction.WEST);
        put(result, Direction.SOUTH, source, org.cube.converter.util.element.Direction.SOUTH);
        return result;
    }

    private static void put(Map<Direction, Float[]> target, Direction targetFace,
                            Map<org.cube.converter.util.element.Direction, Float[]> source,
                            org.cube.converter.util.element.Direction sourceFace) {
        Float[] uv = source.get(sourceFace);
        if (uv != null) {
            target.put(targetFace, uv);
        }
    }
}
