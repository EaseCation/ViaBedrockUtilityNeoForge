package org.oryxel.viabedrockutility.renderer;

import net.minecraft.client.model.geom.ModelPart;
import org.cube.converter.model.element.Locator;
import org.cube.converter.model.element.Parent;
import org.cube.converter.util.element.Position3V;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.oryxel.viabedrockutility.attachable.BedrockTransformConvention;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockPlayerWorldPoseTest {
    private static final float EPSILON = 1.0E-4F;

    @Test
    void capturedHeadAnchorIncludesTheCompleteThirdPersonPresentationStack() {
        final BedrockPlayerModelMetadata metadata = metadata();
        final float entityY = 39.0F;
        final Matrix4f worldPresentation = new Matrix4f()
                .translation(4.0F, entityY, -2.0F)
                .rotateY((float) Math.toRadians(35.0F))
                .scale(-1.0F, -1.0F, 1.0F)
                .scale(0.9375F)
                .translate(0.0F, -1.501F, 0.0F);
        final UUID owner = UUID.randomUUID();

        final BedrockPlayerWorldPose pose = capture(owner, 20L, worldPresentation, metadata);
        final BedrockPlayerWorldPose.Anchor head = pose.bone("head");
        assertNotNull(head);
        assertEquals(BedrockTransformConvention.BindingOffsetFrame.JAVA_MODEL,
                head.bindingOffsetFrame());

        final Vector3f offset = BedrockTransformConvention.bedrockBindingOffsetToJavaModel(
                new Vector3f(-0.38F, -0.2F, 0.1F));
        final Vector3f actual = head.matrix().translate(offset).transformPosition(new Vector3f());
        final Matrix4f expectedMatrix = new Matrix4f(worldPresentation)
                .mul(BedrockTransformConvention.hostAttachment(
                        new Matrix4f(), new Matrix4f(), metadata.bone("head").pivot()))
                .translate(offset);
        final Vector3f expected = expectedMatrix.transformPosition(new Vector3f());
        final Vector3f headOrigin = head.matrix().transformPosition(new Vector3f());
        final Vector3f actorRight = new Vector3f(1.0F, 0.0F, 0.0F)
                .rotateY((float) Math.toRadians(35.0F));

        assertVector(expected, actual);
        assertTrue(new Vector3f(actual).sub(headOrigin).dot(actorRight) > 0.0F,
                "negative Bedrock bind X must remain on the actor's right after Java presentation");
        assertTrue(actual.y > entityY + 1.0F,
                "the head-bound effect must remain near eye height instead of the entity origin");
        assertTrue(pose.isFresh(owner, 20L));
        assertTrue(pose.isFresh(owner, 21L));
        assertFalse(pose.isFresh(owner, 22L));
    }

    @Test
    void publishedMatricesAndLocatorScalePolicyAreImmutable() {
        final BedrockPlayerModelMetadata metadata = metadata();
        final BedrockPlayerWorldPose pose = capture(UUID.randomUUID(), 3L,
                new Matrix4f().translation(8.0F, 12.0F, 4.0F), metadata);
        final BedrockPlayerWorldPose.Anchor locator = pose.locator("muzzle");
        assertNotNull(locator);
        assertTrue(locator.ignoreInheritedScale());

        final Matrix4f modified = locator.matrix().translate(100.0F, 100.0F, 100.0F);
        final Vector3f modifiedPosition = modified.transformPosition(new Vector3f());
        final Vector3f storedPosition = pose.locator("muzzle").matrix().transformPosition(new Vector3f());
        assertTrue(modifiedPosition.distance(storedPosition) > 100.0F);
    }

    private static BedrockPlayerModelMetadata metadata() {
        final BedrockPlayerModelMetadata metadata = new BedrockPlayerModelMetadata(false);
        add(metadata, "root", new Position3V(0, 0, 0), "");
        final Parent head = add(metadata, "head", new Position3V(0, 24, 0), "root");
        head.getLocators().put("muzzle", new Locator(
                new Position3V(-0.38F, 23.8F, 0.1F), new Position3V(0, 0, 0), true));

        // Re-register after adding the locator; metadata snapshots source data at addBone time.
        metadata.addBone(head, "head", "root", "root", new ModelPart(List.of(), Map.of()));
        return metadata;
    }

    private static BedrockPlayerWorldPose capture(UUID owner, long tick, Matrix4f presentation,
                                                   BedrockPlayerModelMetadata metadata) {
        return BedrockPlayerWorldPose.capture(owner, tick, presentation, metadata,
                ignored -> new Matrix4f(), ignored -> new Matrix4f());
    }

    private static Parent add(BedrockPlayerModelMetadata metadata, String name,
                              Position3V pivot, String parentName) {
        final Parent parent = new Parent(name, pivot, new Position3V(0, 0, 0));
        parent.setParent(parentName);
        metadata.addBone(parent, name, parentName, parentName, new ModelPart(List.of(), Map.of()));
        return parent;
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }
}
