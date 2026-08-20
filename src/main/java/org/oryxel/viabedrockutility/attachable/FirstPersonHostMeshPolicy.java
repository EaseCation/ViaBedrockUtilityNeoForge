package org.oryxel.viabedrockutility.attachable;

/** Controls whether a first-person attachable profile submits the host actor's mesh. */
public enum FirstPersonHostMeshPolicy {
    /** The host skeleton still drives binding, but none of its geometry is submitted. */
    HIDDEN,
    /** Draw only the arm that owns the resolved item binding. */
    BOUND_ARM
}
