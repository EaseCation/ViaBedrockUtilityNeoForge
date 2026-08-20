package org.oryxel.viabedrockutility.attachable;

import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Process-wide warn-once dedup for the attachable runtime plus the debug snapshot records. */
public final class AttachableDebugLog {
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private AttachableDebugLog() {
    }

    static void warnOnce(String key, String message, Throwable throwable) {
        if (!WARNED.add(key)) {
            return;
        }
        if (throwable == null) {
            ViaBedrockUtilityNeoForge.LOGGER.warn(message);
        } else {
            ViaBedrockUtilityNeoForge.LOGGER.warn(message, throwable);
        }
    }

    /** Drops the warn-once dedup set so a new pack generation may warn again. */
    static void clearWarned() {
        WARNED.clear();
    }

    public record DebugInfo(AttachableRuntimeRegistry.RuntimeKey runtimeKey,
                            AttachableRuntimeRegistry.RuntimeIdentity identity,
                            String bindingBone, String hostProfile,
                            List<String> semanticChain, List<String> presentationChain,
                            Map<String, String> controllerStates,
                            List<String> renderPasses, float[] hostMatrix) {
        public DebugInfo {
            semanticChain = List.copyOf(semanticChain);
            presentationChain = List.copyOf(presentationChain);
            controllerStates = Map.copyOf(controllerStates);
            renderPasses = List.copyOf(renderPasses);
            hostMatrix = hostMatrix == null ? null : hostMatrix.clone();
        }

        @Override
        public float[] hostMatrix() {
            return hostMatrix == null ? null : hostMatrix.clone();
        }
    }

    public enum AttemptStage {
        PACKS_UNAVAILABLE,
        METADATA_MISSING,
        NO_CANDIDATES,
        CONDITION_REJECTED,
        RUNTIME_REJECTED,
        RUNTIME_EXCEPTION,
        RENDERED
    }

    public record DebugAttempt(AttachableRuntimeRegistry.RuntimeKey runtimeKey, long packGeneration,
                               String itemIdentifier, AttachableQueryContext.ViewContext view,
                               AttemptStage stage, int candidateCount, String attachableIdentifier,
                               List<String> renderPasses, String bindingBone, String detail) {
        public DebugAttempt {
            renderPasses = List.copyOf(renderPasses);
        }
    }
}
