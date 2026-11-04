package org.oryxel.viabedrockutility.mixin.impl.accessor;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PoseStack.Pose.class)
public interface PoseStackPoseAccessor {
    @Accessor("trustedNormals") boolean viaBedrockUtility$trustedNormals();
    @Accessor("trustedNormals") void viaBedrockUtility$setTrustedNormals(boolean value);
}
