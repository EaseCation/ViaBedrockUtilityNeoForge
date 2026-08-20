package org.oryxel.viabedrockutility.renderer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.model.geom.ModelPart;
import org.cube.converter.model.element.Parent;
import org.cube.converter.util.element.Position3V;
import org.junit.jupiter.api.Test;
import org.oryxel.viabedrockutility.attachable.AttachableHostContext;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockPlayerModelMetadataTest {
    private static final float EPSILON = 1.0E-4F;

    @Test
    void semanticAndPresentationChainsRemainIndependent() throws Exception {
        final BedrockPlayerModelMetadata metadata = new BedrockPlayerModelMetadata(false);
        add(metadata, "root", "", "");
        add(metadata, "waist", "root", "root");
        add(metadata, "body", "waist", "root");
        add(metadata, "head", "body", "root");
        add(metadata, "rightArm", "right_arm", "body", "root");
        add(metadata, "rightItem", "rightitem", "right_arm", "right_arm");
        add(metadata, "leftArm", "left_arm", "body", "root");
        add(metadata, "leftItem", "leftitem", "left_arm", "left_arm");

        final BedrockPlayerModelMetadata.Bone rightItem = metadata.bone("rightitem");
        assertNotNull(rightItem);
        assertEquals(List.of("root", "waist", "body", "right_arm", "rightitem"),
                metadata.chainTo(rightItem).stream().map(BedrockPlayerModelMetadata.Bone::key).toList());
        assertEquals(List.of("root", "right_arm", "rightitem"),
                metadata.presentationChainTo(rightItem).stream()
                        .map(BedrockPlayerModelMetadata.Bone::key).toList());

        final AttachableHostContext host = new AttachableHostContext(metadata);
        assertEquals(List.of("root", "waist", "body", "right_arm", "rightitem"),
                host.semanticChain(rightItem));
        assertTrue(host.firstPersonAttachmentMatrix(rightItem)
                .transformPosition(new org.joml.Vector3f()).z < 0.0F);

        final JsonObject fixture;
        try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                "/fixtures/attachable/blockbench_first_person_golden.json"), StandardCharsets.UTF_8)) {
            fixture = JsonParser.parseReader(reader).getAsJsonObject();
        }
        assertGolden(fixture, "right_item_host_matrix_column_major",
                host.firstPersonAttachmentMatrix(rightItem));
        assertGolden(fixture, "left_item_host_matrix_column_major",
                host.firstPersonAttachmentMatrix(metadata.bone("leftitem")));

        final var head = metadata.bone("head");
        final var headMatrix = host.firstPersonAttachmentMatrix(head);
        final var headOrigin = headMatrix.transformPosition(new org.joml.Vector3f());
        final var muzzle = new org.joml.Matrix4f(headMatrix)
                .translate(org.oryxel.viabedrockutility.attachable.BedrockTransformConvention
                        .bedrockBindingOffsetToOwnerAttachment(
                                new org.joml.Vector3f(-0.38F, -0.2F, 0.1F)))
                .transformPosition(new org.joml.Vector3f());
        assertTrue(muzzle.x > headOrigin.x, "negative Bedrock bind X is screen-right in first person");
        assertTrue(muzzle.y < headOrigin.y, "negative Bedrock bind Y is screen-down in first person");
        assertTrue(muzzle.z < headOrigin.z, "positive Bedrock bind Z is forward in first person");
    }

    private static void add(BedrockPlayerModelMetadata metadata, String name,
                            String semanticParent, String presentationParent) {
        add(metadata, name, name, semanticParent, presentationParent);
    }

    private static void add(BedrockPlayerModelMetadata metadata, String sourceName, String modelName,
                            String semanticParent, String presentationParent) {
        final Position3V pivot = switch (sourceName.toLowerCase(java.util.Locale.ROOT)) {
            case "waist" -> new Position3V(0, 12, 0);
            case "body" -> new Position3V(0, 24, 0);
            case "head" -> new Position3V(0, 24, 0);
            case "rightarm" -> new Position3V(-5, 22, 0);
            case "rightitem" -> new Position3V(-6, 15, 1);
            case "leftarm" -> new Position3V(5, 22, 0);
            case "leftitem" -> new Position3V(6, 15, 1);
            default -> new Position3V(0, 0, 0);
        };
        final Parent source = new Parent(sourceName, pivot, new Position3V(0, 0, 0));
        source.setParent(semanticParent);
        metadata.addBone(source, modelName, semanticParent, presentationParent,
                new ModelPart(List.of(), Map.of()));
    }

    private static void assertGolden(JsonObject fixture, String name, org.joml.Matrix4f actual) {
        final float[] values = actual.get(new float[16]);
        for (int index = 0; index < values.length; index++) {
            assertEquals(fixture.getAsJsonArray(name).get(index).getAsFloat(), values[index], EPSILON,
                    name + "[" + index + "]");
        }
    }
}
