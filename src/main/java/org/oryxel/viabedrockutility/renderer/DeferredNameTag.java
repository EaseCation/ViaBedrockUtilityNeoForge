package org.oryxel.viabedrockutility.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    // Cache of Font.PreparedText (the laid-out glyph list) keyed by text content + color/bg + screen offset.
    // vanilla drawInBatch == prepareText(...).visit(...); prepareText (Font$PreparedTextBuilder.accept: glyph
    // lookup + GlyphInstance list build) was ~7.8% of the render thread in JFR because flush() rebuilt it for
    // every name every frame. PreparedText is immutable and mode-independent, so we memoize it and only re-run
    // visit() each frame (visit re-emits vertices with the per-frame pose — that part is unavoidable). The
    // remaining transparency sort / Iris batching is NOT reclaimable and is intentionally left as-is.
    //
    // Key uses text.getString() (String hashCode is cached) rather than the Component itself, to avoid
    // Component.hashCode/equals walking the sibling tree on every lookup. x/y are folded in as raw int bits;
    // for a name tag x = -width/2 so it also disambiguates width-affecting style changes (e.g. bold).
    // Access is render-thread-only (enqueue during entity pass, flush during AfterEntities), no sync needed.
    private record Key(String text, int color, int backgroundColor, int xBits, int yBits) {
    }

    private static final int CACHE_CAP = 256;

    private static final LinkedHashMap<Key, Font.PreparedText> CACHE =
            new LinkedHashMap<>(CACHE_CAP * 2, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Key, Font.PreparedText> eldest) {
                    return size() > CACHE_CAP;
                }
            };

    private DeferredNameTag() {
    }

    /**
     * Drop all cached prepared text. MUST be called on client resource reload: a cached PreparedText holds
     * {@code BakedGlyph} references into the font atlas, which are rebuilt on reload — replaying stale ones
     * would bind a freed/rebaked texture and corrupt the glyphs. Registered as a reload listener in the mod
     * entrypoint.
     */
    public static void clearCache() {
        CACHE.clear();
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
                // Reuse the cached glyph layout; only visit() (vertex emit with this frame's pose) runs per
                // frame. mode is applied by the GlyphVisitor, so the SEE_THROUGH and NORMAL passes share the
                // same cached PreparedText and differ only in the visitor.
                final Font.PreparedText prepared = prepared(e);
                prepared.visit(Font.GlyphVisitor.forMultiBufferSource(bufferSource, e.pose(), e.mode(), e.packedLight()));
            }
            bufferSource.endBatch();
        } finally {
            QUEUE.clear();
        }
    }

    private static Font.PreparedText prepared(final Entry e) {
        final Key key = new Key(e.text().getString(), e.color(), e.backgroundColor(),
                Float.floatToRawIntBits(e.x()), Float.floatToRawIntBits(e.y()));
        Font.PreparedText cached = CACHE.get(key);
        if (cached == null) {
            cached = e.font().prepareText(e.text().getVisualOrderText(), e.x(), e.y(), e.color(), false, e.backgroundColor());
            CACHE.put(key, cached);
        }
        return cached;
    }
}
