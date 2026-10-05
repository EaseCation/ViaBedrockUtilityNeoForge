package org.oryxel.viabedrockutility.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BedrockFrameTimeTest {
    @Test
    void frameDurationIsInSecondsAndSharedByEveryRenderPass() {
        final BedrockFrameTime clock = new BedrockFrameTime();
        clock.beginFrame(1_000_000_000L);
        assertEquals(0.0F, clock.deltaSeconds(), 0.0F);
        clock.beginFrame(1_016_666_667L);
        for (int pass = 0; pass < 20; pass++) {
            assertEquals(1.0F / 60.0F, clock.deltaSeconds(), 1.0e-7F);
        }
        clock.beginFrame(1_116_666_667L);
        assertEquals(0.1F, clock.deltaSeconds(), 1.0e-7F);
    }
}
