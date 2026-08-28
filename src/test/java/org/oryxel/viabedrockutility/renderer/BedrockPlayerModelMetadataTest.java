package org.oryxel.viabedrockutility.renderer;

import net.minecraft.client.model.geom.ModelPart;
import org.cube.converter.model.element.Parent;
import org.cube.converter.util.element.Position3V;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BedrockPlayerModelMetadataTest {
    @Test
    void semanticAndPresentationChainsRemainIndependent() {
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

        final BedrockPlayerModelMetadata.Bone leftItem = metadata.bone("leftitem");
        assertNotNull(leftItem);
        assertEquals(List.of("root", "waist", "body", "left_arm", "leftitem"),
                metadata.chainTo(leftItem).stream().map(BedrockPlayerModelMetadata.Bone::key).toList());
        assertEquals(List.of("root", "left_arm", "leftitem"),
                metadata.presentationChainTo(leftItem).stream()
                        .map(BedrockPlayerModelMetadata.Bone::key).toList());

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
        add(metadata, sourceName, modelName, semanticParent, presentationParent, pivot);
    }

    private static void add(BedrockPlayerModelMetadata metadata, String name,
                            String semanticParent, String presentationParent, Position3V pivot) {
        add(metadata, name, name, semanticParent, presentationParent, pivot);
    }

    private static void add(BedrockPlayerModelMetadata metadata, String sourceName, String modelName,
                            String semanticParent, String presentationParent, Position3V pivot) {
        final Parent source = new Parent(sourceName, pivot, new Position3V(0, 0, 0));
        source.setParent(semanticParent);
        metadata.addBone(source, modelName, semanticParent, presentationParent,
                new ModelPart(List.of(), Map.of()));
    }

}
