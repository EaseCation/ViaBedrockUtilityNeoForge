package org.oryxel.viabedrockutility.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects entity/player name tag draws during the entity render pass and replays them after all
 * entity geometry has been flushed ({@link net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterEntities}).
 *
 * <p>Why: vanilla draws the name in two passes - a faint {@code SEE_THROUGH} pass (no depth write) and
 * an opaque {@code NORMAL} pass (writes depth, LEQUAL). The opaque pass writing depth is what keeps a
 * name from being wrongly overwritten by a farther entity rendered later. VBU custom entities are large
 * {@code Display.ItemDisplay}-backed models whose bodies sit at near/equal depth across a neighbouring
 * name's screen region, so they legitimately pass the LEQUAL test and paint over even the opaque name;
 * the faint pass is never depth-protected and gets painted over too - so the name vanishes entirely.
 *
 * <p>By capturing the fully resolved {@code drawInBatch} arguments (the absolute camera-relative matrix
 * plus the original color/mode/background/light) and replaying them once all bodies are drawn, the faint
 * pass shows through every entity (like it already does through blocks) and the opaque pass shows only
 * where the name is unobscured - independent of entity render order.
 *
 * <p>Render-thread confined: {@link #enqueue} runs during entity rendering and {@link #flush} during the
 * AfterEntities stage, both on the render thread, so no synchronization is needed.
 */
public final class DeferredNameTag {

    private record Entry(Font font, Component text, float x, float y, int color, Matrix4f pose,
                         Font.DisplayMode mode, int backgroundColor, int packedLight) {
    }

    private static final List<Entry> QUEUE = new ArrayList<>();

    private DeferredNameTag() {
    }

    /**
     * Capture one {@code Font.drawInBatch} call for deferred replay. The {@code pose} is the live
     * {@code poseStack.last().pose()} which is reused/mutated after the caller returns, so it is copied here.
     */
    public static void enqueue(final Font font, final Component text, final float x, final float y, final int color,
                               final Matrix4f pose, final Font.DisplayMode mode, final int backgroundColor, final int packedLight) {
        QUEUE.add(new Entry(font, text, x, y, color, new Matrix4f(pose), mode, backgroundColor, packedLight));
    }

    /**
     * Replay all captured name tags (in capture order, so the faint SEE_THROUGH pass precedes the opaque
     * NORMAL pass) after every entity body has been drawn, then flush once and clear.
     */
    public static void flush() {
        if (QUEUE.isEmpty()) {
            return;
        }
        try {
            final MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
            for (final Entry e : QUEUE) {
                e.font().drawInBatch(e.text(), e.x(), e.y(), e.color(), false, e.pose(), bufferSource,
                        e.mode(), e.backgroundColor(), e.packedLight());
            }
            bufferSource.endBatch();
        } finally {
            QUEUE.clear();
        }
    }
}
