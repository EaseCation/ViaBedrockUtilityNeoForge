package org.oryxel.viabedrockutility.attachable;

import java.util.Optional;

@FunctionalInterface
public interface AttachableQueryProvider {
    /** Returns a dependency-free number, boolean, or string for the normalized query name. */
    Optional<AttachableQueryValue> resolve(AttachableQueryContext context, String normalizedQueryName);
}
