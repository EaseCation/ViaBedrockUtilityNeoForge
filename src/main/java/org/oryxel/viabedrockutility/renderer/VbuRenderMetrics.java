package org.oryxel.viabedrockutility.renderer;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/** Property-gated aggregate counters; no event or per-vertex allocation occurs unless explicitly enabled. */
public final class VbuRenderMetrics {
    private static final boolean ENABLED = Boolean.getBoolean("viabedrockutility.renderMetrics");

    private static boolean frameActive;
    private static boolean hasVbuWork;
    private static long modelRenders;
    private static long parts;
    private static long cuboids;
    private static long boxVertices;
    private static long genericVertices;
    private static long sodiumVertices;
    private static long vanillaVertices;
    private static long pushCalls;
    private static long noWriterFallbacks;
    private static long reentrantFallbacks;

    private VbuRenderMetrics() {
    }

    public static void beginFrame() {
        if (!ENABLED) {
            return;
        }
        frameActive = true;
        hasVbuWork = false;
        modelRenders = 0;
        parts = 0;
        cuboids = 0;
        boxVertices = 0;
        genericVertices = 0;
        sodiumVertices = 0;
        vanillaVertices = 0;
        pushCalls = 0;
        noWriterFallbacks = 0;
        reentrantFallbacks = 0;
    }

    public static void endFrame() {
        if (!ENABLED || !frameActive) {
            return;
        }
        frameActive = false;
        if (!hasVbuWork) {
            return;
        }

        final RenderFrameEvent event = new RenderFrameEvent();
        if (event.isEnabled()) {
            event.modelRenders = modelRenders;
            event.parts = parts;
            event.cuboids = cuboids;
            event.boxVertices = boxVertices;
            event.genericVertices = genericVertices;
            event.sodiumVertices = sodiumVertices;
            event.vanillaVertices = vanillaVertices;
            event.pushCalls = pushCalls;
            event.noWriterFallbacks = noWriterFallbacks;
            event.reentrantFallbacks = reentrantFallbacks;
            event.commit();
        }
    }

    public static void recordModelRender() {
        if (ENABLED && frameActive) {
            hasVbuWork = true;
            modelRenders++;
        }
    }

    public static void recordPart() {
        if (ENABLED && frameActive) {
            hasVbuWork = true;
            parts++;
        }
    }

    public static void recordBatch(VbuCuboidBatchRenderer.Batch batch) {
        if (!ENABLED || !frameActive) {
            return;
        }
        hasVbuWork = true;
        cuboids += batch.cuboidCount();
        boxVertices += batch.boxVertexCount();
        genericVertices += batch.genericVertexCount();
        sodiumVertices += batch.totalVertexCount();
        pushCalls++;
    }

    public static void recordEmptyBatch(VbuCuboidBatchRenderer.Batch batch) {
        if (ENABLED && frameActive) {
            hasVbuWork = true;
            cuboids += batch.cuboidCount();
        }
    }

    public static void recordEmptyCuboid() {
        if (ENABLED && frameActive) {
            hasVbuWork = true;
            cuboids++;
        }
    }

    public static void recordSinglePush(VbuCompiledCuboid geometry) {
        if (!ENABLED || !frameActive) {
            return;
        }
        hasVbuWork = true;
        cuboids++;
        if (geometry.isBox()) {
            boxVertices += geometry.vertexCount();
        } else {
            genericVertices += geometry.vertexCount();
        }
        sodiumVertices += geometry.vertexCount();
        pushCalls++;
    }

    public static void recordFallback(VbuCompiledCuboid geometry, boolean writerAvailable) {
        if (!ENABLED || !frameActive) {
            return;
        }
        hasVbuWork = true;
        cuboids++;
        if (geometry.isBox()) {
            boxVertices += geometry.vertexCount();
        } else {
            genericVertices += geometry.vertexCount();
        }
        vanillaVertices += geometry.vertexCount();
        if (writerAvailable) {
            reentrantFallbacks++;
        } else {
            noWriterFallbacks++;
        }
    }

    @Name("org.oryxel.viabedrockutility.RenderFrame")
    @Label("VBU Render Frame")
    @Category({"ViaBedrockUtility", "Rendering"})
    @StackTrace(false)
    public static final class RenderFrameEvent extends Event {
        @Label("Model Renders") public long modelRenders;
        @Label("Indexed Parts") public long parts;
        @Label("Cuboids") public long cuboids;
        @Label("Box Vertices") public long boxVertices;
        @Label("Generic Vertices") public long genericVertices;
        @Label("Sodium Vertices") public long sodiumVertices;
        @Label("Vanilla Vertices") public long vanillaVertices;
        @Label("Bulk Push Calls") public long pushCalls;
        @Label("No Writer Fallbacks") public long noWriterFallbacks;
        @Label("Reentrant Fallbacks") public long reentrantFallbacks;
    }
}
