package org.oryxel.viabedrockutility.attachable;

/** Lazily submits host geometry only after an attachable has a renderable pass. */
@FunctionalInterface
public interface FirstPersonHostMeshRenderer {
    void renderBoundArm();
}
