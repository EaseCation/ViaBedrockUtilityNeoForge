package org.oryxel.viabedrockutility.renderer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoseMeshRebuildBudgetTest {
    @AfterEach
    void clearBudget() {
        PoseMeshRebuildBudget.clearForTesting();
    }

    @Test
    void capsEveryFrameAndRotatesAcrossStableRenderOrder() {
        List<List<Integer>> grantedByFrame = new ArrayList<>();
        for (int frame = 0; frame < 3; frame++) {
            PoseMeshRebuildBudget.reset(2);
            List<Integer> granted = new ArrayList<>();
            for (int entity = 0; entity < 6; entity++) {
                if (PoseMeshRebuildBudget.tryAcquire()) {
                    granted.add(entity);
                }
            }
            PoseMeshRebuildBudget.endFrame();
            grantedByFrame.add(granted);
        }

        assertEquals(List.of(List.of(0, 1), List.of(2, 3), List.of(4, 5)), grantedByFrame);
    }

    @Test
    void unlimitedModeAcceptsEveryRequest() {
        PoseMeshRebuildBudget.reset(0);
        for (int entity = 0; entity < 100; entity++) {
            assertTrue(PoseMeshRebuildBudget.tryAcquire());
        }
    }
}
