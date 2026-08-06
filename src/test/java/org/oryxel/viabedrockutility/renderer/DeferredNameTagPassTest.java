package org.oryxel.viabedrockutility.renderer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeferredNameTagPassTest {
    @AfterEach
    void closePass() {
        DeferredNameTag.endMainEntityPass();
    }

    @Test
    void onlyAcceptsCaptureInsideMainEntityWindow() {
        assertFalse(DeferredNameTag.isMainEntityPassActive());

        DeferredNameTag.beginMainEntityPass();

        assertTrue(DeferredNameTag.isMainEntityPassActive());
    }

    @Test
    void emptyFlushClosesCaptureWindow() {
        DeferredNameTag.beginMainEntityPass();

        DeferredNameTag.flush();

        assertFalse(DeferredNameTag.isMainEntityPassActive());
    }
}
