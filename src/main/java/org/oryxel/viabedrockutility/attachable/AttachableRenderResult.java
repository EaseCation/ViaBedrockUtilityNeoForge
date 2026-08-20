package org.oryxel.viabedrockutility.attachable;

/** Outcome of an owner-bound attachable attempt, including whether vanilla must be suppressed. */
public enum AttachableRenderResult {
    /** No attachable definition applies; the caller may continue with the vanilla item path. */
    NOT_APPLICABLE,
    /** The attachable was submitted successfully; the caller must not submit vanilla as well. */
    RENDERED,
    /** An attachable applies but cannot produce geometry; preserve Bedrock's invisible result. */
    SUPPRESSED
}
