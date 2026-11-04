package org.oryxel.viabedrockutility.attachable;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Read-only last-stage observation for the generic player animation diagnostic command. */
public final class FirstPersonRenderTrace {
    private static final ThreadLocal<UUID> OWNER = new ThreadLocal<>();
    private static final Map<UUID, Snapshot> LAST = new ConcurrentHashMap<>();
    private static BoneCapture boneCapture;

    /** One actual display frame, bounded and off by default. No pose is reevaluated. */
    public static void captureBones(UUID owner, java.util.List<String> names) {
        if (owner == null || names.isEmpty() || names.size() > 16
                || names.stream().anyMatch(name -> name == null || name.isBlank() || name.length() > 256)) {
            throw new IllegalArgumentException("Select 1..16 bounded bone names");
        }
        boneCapture = new BoneCapture(owner, java.util.Set.copyOf(names), System.nanoTime() + 2_000_000_000L);
    }

    public static void clearBoneCapture() { boneCapture = null; }

    public static Map<String, Object> capturedBones() {
        BoneCapture capture = boneCapture;
        if (capture == null) return Map.of();
        if (System.nanoTime() >= capture.deadline
                || org.oryxel.viabedrockutility.animation.BedrockFrameTime.INSTANCE.frame() > capture.frame) capture.done = true;
        return Map.of("complete", capture.done, "frame", capture.frame,
                "truncated", capture.truncated, "parts", java.util.List.copyOf(capture.parts));
    }

    public static void recordBone(String name, boolean cubeGroup, PoseStack poses) {
        BoneCapture capture = boneCapture;
        if (capture == null || capture.done) return;
        if (System.nanoTime() >= capture.deadline) { capture.done = true; return; }
        if (!capture.owner.equals(OWNER.get())) return;
        long frame = org.oryxel.viabedrockutility.animation.BedrockFrameTime.INSTANCE.frame();
        if (frame < capture.frame) return;
        if (frame > capture.frame) { capture.done = true; return; }
        if (!capture.names.contains(name)) return;
        if (capture.parts.size() >= 128) { capture.done = capture.truncated = true; return; }
        capture.parts.add(Map.of("bone", name, "cube_group", cubeGroup,
                "pose", poses.last().pose().get(new float[16]),
                "final_pose", new Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix())
                        .mul(poses.last().pose()).get(new float[16])));
    }

    private static final class BoneCapture {
        final UUID owner;
        final java.util.Set<String> names;
        final long deadline;
        final java.util.List<Map<String, Object>> parts = new java.util.ArrayList<>();
        final long frame = org.oryxel.viabedrockutility.animation.BedrockFrameTime.INSTANCE.frame() + 1;
        boolean done, truncated;
        BoneCapture(UUID owner, java.util.Set<String> names, long deadline) {
            this.owner = owner; this.names = names; this.deadline = deadline;
        }
    }

    private FirstPersonRenderTrace() {
    }

    public static void begin(UUID owner, long frameToken) {
        OWNER.set(owner);
        CONTEXT.set(new Context(frameToken));
        LAST.compute(owner, (ignored, previous) -> previous == null || previous.frameToken() != frameToken
                ? null : previous);
    }

    public static void end() {
        OWNER.remove();
        CONTEXT.remove();
    }

    public static void record(String stage, HumanoidArm arm, PoseStack poses) {
        if (poses == null) {
            return;
        }
        recordMatrix(stage, arm, poses.last().pose());
    }

    /** Records a fully composed submission matrix without mutating the caller's PoseStack. */
    public static void recordMatrix(String stage, HumanoidArm arm, Matrix4f matrix) {
        final UUID owner = OWNER.get();
        if (owner == null || matrix == null) {
            return;
        }
        final Context context = CONTEXT.get();
        if (context == null) return;
        LAST.compute(owner, (ignored, previous) -> {
            final Map<String, Stage> stages = new LinkedHashMap<>(previous == null
                    || previous.frameToken() != context.frameToken() ? Map.of() : previous.stages());
            stages.put(stage, new Stage(arm == null ? "none"
                    : arm.name().toLowerCase(java.util.Locale.ROOT), matrix.get(new float[16])));
            final Snapshot next = new Snapshot(context.frameToken(), stage,
                    arm == null ? "none" : arm.name().toLowerCase(java.util.Locale.ROOT),
                    matrix.get(new float[16]), stages);
            if (previous != null && previous.frameToken() == context.frameToken()
                    && stageRank(stage) < stageRank(previous.stage())) {
                return new Snapshot(previous.frameToken(), previous.stage(), previous.arm(),
                        previous.pose(), stages);
            }
            return next;
        });
    }

    public static Snapshot snapshot(UUID owner) {
        return owner == null ? null : LAST.get(owner);
    }

    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();

    public record Snapshot(long frameToken, String stage, String arm, float[] pose,
                           Map<String, Stage> stages) {
        public Snapshot {
            pose = pose == null ? new float[16] : Arrays.copyOf(pose, pose.length);
            stages = Map.copyOf(stages == null ? Map.of() : stages);
        }

        @Override
        public float[] pose() {
            return Arrays.copyOf(pose, pose.length);
        }

    }

    public record Stage(String arm, float[] pose) {
        public Stage {
            pose = pose == null ? new float[16] : Arrays.copyOf(pose, pose.length);
        }

        @Override
        public float[] pose() {
            return Arrays.copyOf(pose, pose.length);
        }
    }

    private static int stageRank(String stage) {
        return switch (stage) {
            case "final_vertex", "final_modelpart" -> 4;
            case "arm_submit", "item_submit", "arm_modelpart", "arm_vertex",
                    "arm_modelpart_submit", "arm_writer", "arm_compile" -> 3;
            case "camera", "global_modelview" -> 2;
            default -> 1;
        };
    }

    private record Context(long frameToken) {}
}
