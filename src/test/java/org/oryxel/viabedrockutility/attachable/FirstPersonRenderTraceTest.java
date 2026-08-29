package org.oryxel.viabedrockutility.attachable;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FirstPersonRenderTraceTest {
    @Test
    void keepsHighestStageWithinOneHandRenderFrame() {
        UUID owner = UUID.randomUUID();
        PoseStack poses = new PoseStack();
        FirstPersonRenderTrace.begin(owner, 11L);
        try {
            FirstPersonRenderTrace.record("event", HumanoidArm.RIGHT, poses);
            FirstPersonRenderTrace.record("arm_prefix", HumanoidArm.RIGHT, poses);
            FirstPersonRenderTrace.recordMatrix("arm_submit", HumanoidArm.RIGHT, poses.last().pose());
            FirstPersonRenderTrace.record("camera", HumanoidArm.LEFT, poses);
        } finally {
            FirstPersonRenderTrace.end();
        }
        assertEquals("arm_submit", FirstPersonRenderTrace.snapshot(owner).stage());
        assertEquals(4, FirstPersonRenderTrace.snapshot(owner).stages().size());
        assertEquals("left", FirstPersonRenderTrace.snapshot(owner).stages().get("camera").arm());
    }

    @Test
    void startsASeparateTraceForTheNextRenderFrame() {
        UUID owner = UUID.randomUUID();
        PoseStack poses = new PoseStack();
        FirstPersonRenderTrace.begin(owner, 1L);
        FirstPersonRenderTrace.record("arm_submit", HumanoidArm.RIGHT, poses);
        FirstPersonRenderTrace.end();
        FirstPersonRenderTrace.begin(owner, 2L);
        try {
            FirstPersonRenderTrace.record("event", HumanoidArm.LEFT, poses);
        } finally {
            FirstPersonRenderTrace.end();
        }
        assertEquals("event", FirstPersonRenderTrace.snapshot(owner).stage());
    }

    @Test
    void keepsTheActualModelPartSubmissionStageAfterArmSubmit() {
        UUID owner = UUID.randomUUID();
        PoseStack poses = new PoseStack();
        FirstPersonRenderTrace.begin(owner, 21L);
        try {
            FirstPersonRenderTrace.record("arm_submit", HumanoidArm.RIGHT, poses);
            FirstPersonRenderTrace.record("arm_modelpart", HumanoidArm.RIGHT, poses);
            FirstPersonRenderTrace.record("arm_vertex", HumanoidArm.RIGHT, poses);
            FirstPersonRenderTrace.record("arm_writer", HumanoidArm.RIGHT, poses);
        } finally {
            FirstPersonRenderTrace.end();
        }
        assertEquals("arm_writer", FirstPersonRenderTrace.snapshot(owner).stage());
        assertEquals(4, FirstPersonRenderTrace.snapshot(owner).stages().size());
    }

    @Test
    void finalVertexStageOutranksTheLocalWriterStage() {
        UUID owner = UUID.randomUUID();
        PoseStack poses = new PoseStack();
        FirstPersonRenderTrace.begin(owner, 31L);
        try {
            FirstPersonRenderTrace.record("arm_writer", HumanoidArm.RIGHT, poses);
            FirstPersonRenderTrace.record("global_modelview", HumanoidArm.RIGHT, poses);
            FirstPersonRenderTrace.record("final_vertex", HumanoidArm.RIGHT, poses);
        } finally {
            FirstPersonRenderTrace.end();
        }
        assertEquals("final_vertex", FirstPersonRenderTrace.snapshot(owner).stage());
        assertEquals(3, FirstPersonRenderTrace.snapshot(owner).stages().size());
    }
}
