package org.oryxel.viabedrockutility.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.easecation.bedrockmotion.model.EntityRotationTransform;
import net.easecation.bedrockmotion.util.MathUtil;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;

/** Render-scoped actor basis, captured before any skeleton or item binding transform.
 * Reused per render thread, including nested entity rendering; never kept on a shared resource.
 */
public final class BedrockEntityRenderSpace {
    public interface Scope extends AutoCloseable { @Override void close(); }
    private static final Scope UNCHANGED = () -> {};
    private static final ThreadLocal<Stack> STACK = ThreadLocal.withInitial(Stack::new);
    private static final class Stack {
        final Frame[] frames = new Frame[16];
        int depth;
    }
    private static final class Frame implements Scope {
        final Stack owner;
        final int index;
        final Matrix4f entity = new Matrix4f();
        final Matrix4f local = new Matrix4f();
        final Matrix4f scratch = new Matrix4f();
        final Matrix4f result = new Matrix4f();
        final Quaternionf rotation = new Quaternionf();
        Frame(Stack owner, int index) { this.owner = owner; this.index = index; }
        @Override public void close() {
            if (owner.depth != index + 1) throw new IllegalStateException("Unbalanced Bedrock render space");
            owner.depth--;
        }
    }
    private BedrockEntityRenderSpace() {}

    public static Scope enterIfAbsent(PoseStack poses) {
        return STACK.get().depth == 0 ? enter(poses, null) : UNCHANGED;
    }

    /** Optional prefix is the FP camera basis, not the physical hand or geometry installation. */
    public static Scope enter(PoseStack poses, Matrix4fc prefix) {
        Stack stack = STACK.get();
        if (stack.depth == stack.frames.length) throw new IllegalStateException("Nested Bedrock render limit");
        Frame frame = stack.frames[stack.depth];
        if (frame == null) stack.frames[stack.depth] = frame = new Frame(stack, stack.depth);
        frame.entity.set(poses.last().pose());
        if (prefix != null) frame.entity.mul(prefix);
        stack.depth++;
        return frame;
    }

    private static Frame current() {
        Stack stack = STACK.get();
        if (stack.depth == 0) throw new IllegalStateException("Entity-relative pose outside a render space");
        return stack.frames[stack.depth - 1];
    }

    /** Same matrix primitive as semantic pose/locator readback. No inverse of animated ancestors. */
    public static void apply(PoseStack poses, ModelPart part) {
        Frame frame = current();
        IModelPart extension = (IModelPart)(Object)part;
        Vector3f rotation = extension.viaBedrockUtility$getRotation();
        Vector3f pivot = extension.viaBedrockUtility$getPivot();
        frame.local.identity().rotateZYX(rotation.z * MathUtil.DEGREES_TO_RADIANS,
                rotation.y * MathUtil.DEGREES_TO_RADIANS, rotation.x * MathUtil.DEGREES_TO_RADIANS)
                .rotateZYX(part.zRot, part.yRot, part.xRot).scale(part.xScale, part.yScale, part.zScale);
        EntityRotationTransform.apply(frame.result.set(poses.last().pose()), frame.entity, frame.local,
                pivot.x / 16, pivot.y / 16, pivot.z / 16, frame.scratch);
        // Reset normals as well: multiplying an inverse parent fails for hidden (zero-scale) bones.
        poses.setIdentity();
        poses.mulPose(frame.entity);
        poses.mulPose(frame.rotation.rotationZYX(rotation.z * MathUtil.DEGREES_TO_RADIANS,
                rotation.y * MathUtil.DEGREES_TO_RADIANS, rotation.x * MathUtil.DEGREES_TO_RADIANS));
        poses.mulPose(frame.rotation.rotationZYX(part.zRot, part.yRot, part.xRot));
        poses.scale(part.xScale, part.yScale, part.zScale);
        poses.last().pose().set(frame.result);
    }

    /** A flattened player part whose semantic ancestors include an entity-relative bone. */
    public static void applyAbsolute(PoseStack poses, Matrix4fc modelPose) {
        Frame frame = current();
        poses.setIdentity();
        poses.mulPose(frame.entity);
        poses.mulPose(modelPose);
    }
}
