package org.oryxel.viabedrockutility.mixin.interfaces;

import org.oryxel.viabedrockutility.renderer.VbuCompiledCuboid;

public interface ICuboid {
    boolean viaBedrockUtility$isVBUCuboid();
    boolean viaBedrockUtility$hasAnimatedUv();
    void viaBedrockUtility$watchGeometry(Runnable listener);
    void viaBedrockUtility$unwatchGeometry(Runnable listener);
    void viaBedrockUtility$markAsVBU();
    void viaBedrockUtility$markAsVBUBox(float x0, float y0, float z0, float x1, float y1, float z1);
    void viaBedrockUtility$rebuildCompiledGeometry();
    VbuCompiledCuboid viaBedrockUtility$getCompiledGeometry();
    float viaBedrockUtility$getVOffset();
    void viaBedrockUtility$setVOffset(float offset);
}
