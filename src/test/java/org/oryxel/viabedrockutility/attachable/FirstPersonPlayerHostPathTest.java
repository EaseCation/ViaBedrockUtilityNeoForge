package org.oryxel.viabedrockutility.attachable;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstPersonPlayerHostPathTest {
    private static final String RENDERER_CLASS =
            "/org/oryxel/viabedrockutility/attachable/FirstPersonAttachableRenderer.class";

    @Test
    void firstPersonHostCannotFallBackToInheritedJavaArmPose() throws IOException {
        final String bytecode = readClassBytecode(RENDERER_CLASS);

        assertTrue(bytecode.contains("FirstPersonBedrockArmRenderer"));
        assertFalse(bytecode.contains("renderRightHand"));
        assertFalse(bytecode.contains("renderLeftHand"));
        assertFalse(bytecode.contains("arm-fallback"));
    }

    private static String readClassBytecode(String resource) throws IOException {
        try (InputStream input = FirstPersonPlayerHostPathTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, "compiled class must be present: " + resource);
            return new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
