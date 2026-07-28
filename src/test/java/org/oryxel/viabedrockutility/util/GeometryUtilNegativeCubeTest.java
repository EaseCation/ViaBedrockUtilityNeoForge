package org.oryxel.viabedrockutility.util;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeometryUtilNegativeCubeTest {
    @Test
    void cinemaBackgroundKeepsNorthFaceAtOriginPlane() {
        Map<org.cube.converter.util.element.Direction, Float[]> bedrockFaces = new EnumMap<>(
                org.cube.converter.util.element.Direction.class);
        bedrockFaces.put(org.cube.converter.util.element.Direction.NORTH,
                new Float[]{1F, 64F, 0F, 0F});
        bedrockFaces.put(org.cube.converter.util.element.Direction.EAST,
                new Float[]{1F, 64F, 0F, 0F});
        bedrockFaces.put(org.cube.converter.util.element.Direction.WEST,
                new Float[]{1F, 64F, 0F, 0F});
        bedrockFaces.put(org.cube.converter.util.element.Direction.UP,
                new Float[]{0F, 63F, 1F, 64F});
        bedrockFaces.put(org.cube.converter.util.element.Direction.DOWN,
                new Float[]{0F, 0F, 1F, 1F});

        Map<Direction, Float[]> javaFaces = BedrockCubeFaceMapping.toJavaFaces(bedrockFaces);
        ModelPart.Cube cube = new ModelPart.Cube(0, 0, 279F, 39.016F, 0F,
                -558F, -302F, -80F, 0F, 0F, 0F, false, 128F, 128F,
                javaFaces.keySet());
        GeometryUtil.correctUv(cube, javaFaces, 128F, 128F, 0F, false);

        List<ModelPart.Polygon> northFaces = List.of(cube.polygons).stream()
                .filter(face -> face.normal().z() == -1F)
                .toList();
        List<ModelPart.Polygon> southFaces = List.of(cube.polygons).stream()
                .filter(face -> face.normal().z() == 1F)
                .toList();
        assertEquals(1, northFaces.size());
        assertEquals(0, southFaces.size());

        ModelPart.Vertex[] vertices = northFaces.getFirst().vertices();
        for (ModelPart.Vertex vertex : vertices) {
            assertEquals(0F, vertex.pos().z());
        }
        assertEquals(0F, vertices[0].u());
        assertEquals(0.5F, vertices[0].v());
        assertEquals(1F / 128F, vertices[1].u());
        assertEquals(0.5F, vertices[1].v());
        assertEquals(1F / 128F, vertices[2].u());
        assertEquals(0F, vertices[2].v());
        assertEquals(0F, vertices[3].u());
        assertEquals(0F, vertices[3].v());
    }
}
