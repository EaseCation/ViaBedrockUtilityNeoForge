package org.oryxel.viabedrockutility.attachable;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerHostSharedPosePathTest {
    @Test
    void visibleArmAndAttachableAnchorsUseTheCurrentHostPose() throws IOException {
        final String hostContext = readClassBytecode(
                "/org/oryxel/viabedrockutility/attachable/AttachableHostContext.class");
        final String armRenderer = readClassBytecode(
                "/org/oryxel/viabedrockutility/attachable/FirstPersonBedrockArmRenderer.class");
        final String attachableRuntime = readClassBytecode(
                "/org/oryxel/viabedrockutility/attachable/AttachableRuntimeInstance.class");

        assertTrue(hostContext.contains("BedrockModelPartTransform"));
        assertTrue(hostContext.contains("current"));
        assertTrue(hostContext.contains("firstPersonArmRenderPrefix"));
        assertTrue(hostContext.contains("firstPersonAttachmentMatrix"));
        assertTrue(hostContext.contains("attachmentMatrix"));
        assertTrue(armRenderer.contains("firstPersonArmRenderPrefix"));
        assertTrue(attachableRuntime.contains("firstPersonAttachmentMatrix"));
        assertTrue(attachableRuntime.contains("attachmentMatrix"));
    }

    private static String readClassBytecode(String resource) throws IOException {
        try (InputStream input = PlayerHostSharedPosePathTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, "compiled class must be present: " + resource);
            return new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
