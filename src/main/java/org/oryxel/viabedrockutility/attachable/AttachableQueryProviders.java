package org.oryxel.viabedrockutility.attachable;

import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import java.util.Locale;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

public final class AttachableQueryProviders {
    private static final CopyOnWriteArrayList<Registration> PROVIDERS = new CopyOnWriteArrayList<>();
    private static final Set<String> WARNED_UNKNOWN = ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED_PROVIDER_FAILURE = ConcurrentHashMap.newKeySet();

    private AttachableQueryProviders() {
    }

    public static void register(String ownerModId, AttachableQueryProvider provider) {
        register(ownerModId, Set.of(), provider);
    }

    public static void register(String ownerModId, Set<String> queryNames,
                                AttachableQueryProvider provider) {
        PROVIDERS.removeIf(registration -> registration.ownerModId().equals(ownerModId));
        final Set<String> normalizedNames = new LinkedHashSet<>();
        queryNames.forEach(name -> normalizedNames.add(normalize(name)));
        PROVIDERS.add(new Registration(ownerModId, Set.copyOf(normalizedNames), provider));
    }

    public static void unregister(String ownerModId) {
        PROVIDERS.removeIf(registration -> registration.ownerModId().equals(ownerModId));
    }

    public static Optional<AttachableQueryValue> resolve(AttachableQueryContext context, String queryName, String expression) {
        final Optional<AttachableQueryValue> resolved = resolveIfHandled(context, queryName);
        if (resolved.isPresent()) {
            return resolved;
        }
        final String normalized = normalize(queryName);
        final String warningKey = context.attachableIdentifier() + '\0' + expression;
        if (WARNED_UNKNOWN.add(warningKey)) {
            ViaBedrockUtilityNeoForge.LOGGER.warn(
                    "[Attachable] Unknown query '{}' in {} expression '{}'",
                    normalized, context.attachableIdentifier(), expression);
        }
        return Optional.empty();
    }

    /** Resolves a known parameterized query, such as query.property(name), without unknown-query noise. */
    public static Optional<AttachableQueryValue> resolveIfHandled(AttachableQueryContext context, String queryName) {
        final String normalized = normalize(queryName);
        for (Registration registration : PROVIDERS) {
            try {
                final Optional<AttachableQueryValue> value = registration.provider().resolve(context, normalized);
                if (value.isPresent()) {
                    return value;
                }
            } catch (Throwable throwable) {
                final String warningKey = registration.ownerModId() + '\0'
                        + context.attachableIdentifier() + '\0' + normalized;
                if (WARNED_PROVIDER_FAILURE.add(warningKey)) {
                    ViaBedrockUtilityNeoForge.LOGGER.warn(
                            "[Attachable] Query provider '{}' failed for '{}' in {}; continuing",
                            registration.ownerModId(), normalized, context.attachableIdentifier(), throwable);
                }
            }
        }
        return Optional.empty();
    }

    public static boolean isNamespace(String queryPrefix) {
        final String prefix = normalize(queryPrefix) + ".";
        for (Registration registration : PROVIDERS) {
            if (registration.queryNames().stream().anyMatch(name -> name.startsWith(prefix))) {
                return true;
            }
        }
        return false;
    }

    public static String normalize(String queryName) {
        String normalized = queryName.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("query.")) {
            normalized = normalized.substring(6);
        } else if (normalized.startsWith("q.")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    /** Test hook: size of the unknown-query warn-once dedup set. */
    static int warnedUnknownCount() {
        return WARNED_UNKNOWN.size();
    }

    static void clearDiagnostics() {
        WARNED_UNKNOWN.clear();
        WARNED_PROVIDER_FAILURE.clear();
    }

    private record Registration(String ownerModId, Set<String> queryNames,
                                AttachableQueryProvider provider) {
    }
}
