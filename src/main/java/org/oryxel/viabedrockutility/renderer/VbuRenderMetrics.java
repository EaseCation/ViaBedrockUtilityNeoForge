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
    private static long frozenCandidates;
    private static long frozenBakeUploads;
    private static long frozenCacheHits;
    private static long frozenStaticVertices;
    private static long frozenUploadBytes;
    private static long frozenDrawCalls;
    private static long frozenFallbacks;
    private static long frozenInvalidations;
    private static long frozenEligibilityFallbacks;
    private static long frozenUploadFallbacks;
    private static long frozenDrawFallbacks;
    private static long frozenNearInvalidations;
    private static long frozenContentInvalidations;
    private static long frozenReloadInvalidations;
    private static long frozenLruInvalidations;
    private static long frozenLifecycleInvalidations;

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
        frozenCandidates = 0;
        frozenBakeUploads = 0;
        frozenCacheHits = 0;
        frozenStaticVertices = 0;
        frozenUploadBytes = 0;
        frozenDrawCalls = 0;
        frozenFallbacks = 0;
        frozenInvalidations = 0;
        frozenEligibilityFallbacks = 0;
        frozenUploadFallbacks = 0;
        frozenDrawFallbacks = 0;
        frozenNearInvalidations = 0;
        frozenContentInvalidations = 0;
        frozenReloadInvalidations = 0;
        frozenLruInvalidations = 0;
        frozenLifecycleInvalidations = 0;
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
            event.frozenCandidates = frozenCandidates;
            event.frozenBakeUploads = frozenBakeUploads;
            event.frozenCacheHits = frozenCacheHits;
            event.frozenStaticVertices = frozenStaticVertices;
            event.frozenUploadBytes = frozenUploadBytes;
            event.frozenDrawCalls = frozenDrawCalls;
            event.frozenFallbacks = frozenFallbacks;
            event.frozenInvalidations = frozenInvalidations;
            event.frozenEligibilityFallbacks = frozenEligibilityFallbacks;
            event.frozenUploadFallbacks = frozenUploadFallbacks;
            event.frozenDrawFallbacks = frozenDrawFallbacks;
            event.frozenNearInvalidations = frozenNearInvalidations;
            event.frozenContentInvalidations = frozenContentInvalidations;
            event.frozenReloadInvalidations = frozenReloadInvalidations;
            event.frozenLruInvalidations = frozenLruInvalidations;
            event.frozenLifecycleInvalidations = frozenLifecycleInvalidations;
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

    public static void recordFrozenCandidate() {
        if (ENABLED && frameActive) {
            hasVbuWork = true;
            frozenCandidates++;
        }
    }

    public static void recordFrozenUpload(long bytes) {
        if (ENABLED && frameActive) {
            hasVbuWork = true;
            frozenBakeUploads++;
            frozenUploadBytes += bytes;
        }
    }

    public static void recordFrozenCacheHit() {
        if (ENABLED && frameActive) {
            hasVbuWork = true;
            frozenCacheHits++;
        }
    }

    public static void recordFrozenDraw(long vertices) {
        if (ENABLED && frameActive) {
            hasVbuWork = true;
            frozenDrawCalls++;
            frozenStaticVertices += vertices;
        }
    }

    public static void recordFrozenFallback(String reason, long count) {
        if (!ENABLED || !frameActive) {
            return;
        }
        hasVbuWork = true;
        frozenFallbacks += count;
        if ("eligibility".equals(reason) || "too_small".equals(reason)) {
            frozenEligibilityFallbacks += count;
        } else if ("upload_error".equals(reason) || "cache_full".equals(reason)) {
            frozenUploadFallbacks += count;
        } else if ("draw_error".equals(reason)) {
            frozenDrawFallbacks += count;
        }
    }

    public static void recordFrozenInvalidation(String reason) {
        if (ENABLED && frameActive) {
            hasVbuWork = true;
            frozenInvalidations++;
            switch (reason) {
                case "near_transition" -> frozenNearInvalidations++;
                case "light", "material", "model_change" -> frozenContentInvalidations++;
                case "resource_reload" -> frozenReloadInvalidations++;
                case "lru" -> frozenLruInvalidations++;
                case "entity_removed", "disconnect", "client_close" -> frozenLifecycleInvalidations++;
                default -> { }
            }
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
        @Label("Frozen Candidates") public long frozenCandidates;
        @Label("Frozen Bake Uploads") public long frozenBakeUploads;
        @Label("Frozen Cache Hits") public long frozenCacheHits;
        @Label("Frozen Static Vertices") public long frozenStaticVertices;
        @Label("Frozen Upload Bytes") public long frozenUploadBytes;
        @Label("Frozen Draw Calls") public long frozenDrawCalls;
        @Label("Frozen Fallbacks") public long frozenFallbacks;
        @Label("Frozen Invalidations") public long frozenInvalidations;
        @Label("Frozen Eligibility Fallbacks") public long frozenEligibilityFallbacks;
        @Label("Frozen Upload Fallbacks") public long frozenUploadFallbacks;
        @Label("Frozen Draw Fallbacks") public long frozenDrawFallbacks;
        @Label("Frozen Near Invalidations") public long frozenNearInvalidations;
        @Label("Frozen Content Invalidations") public long frozenContentInvalidations;
        @Label("Frozen Reload Invalidations") public long frozenReloadInvalidations;
        @Label("Frozen LRU Invalidations") public long frozenLruInvalidations;
        @Label("Frozen Lifecycle Invalidations") public long frozenLifecycleInvalidations;
    }
}
