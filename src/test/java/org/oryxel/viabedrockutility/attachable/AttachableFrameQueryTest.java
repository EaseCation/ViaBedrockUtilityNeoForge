package org.oryxel.viabedrockutility.attachable;

import net.easecation.bedrockmotion.mocha.MoLangEngine;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttachableFrameQueryTest {
    @Test
    void attachableUsesTheSameNormalizedFrameAlphaAsPlayerHost() throws Exception {
        final AttachableScopeFactory.RuntimeScope frame = AttachableScopeFactory.RuntimeScope.temporary(
                AttachableOwnerSnapshot.EMPTY, null, AttachableItemSnapshot.EMPTY,
                AttachableQueryContext.LogicalHand.MAIN_HAND, HumanoidArm.RIGHT,
                AttachableQueryContext.ViewContext.FIRST_PERSON, 10L, 0.75F, "test:frame");
        assertEquals(0.75, MoLangEngine.eval(frame.scope(), "query.frame_alpha").getAsNumber(), 0.0);
        assertEquals(0.75, MoLangEngine.eval(frame.scope(), "q.frame_alpha").getAsNumber(), 0.0);
    }
}
