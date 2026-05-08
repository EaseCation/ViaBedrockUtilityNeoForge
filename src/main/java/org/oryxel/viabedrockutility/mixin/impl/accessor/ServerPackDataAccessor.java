package org.oryxel.viabedrockutility.mixin.impl.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.file.Path;

@Mixin(targets = "net.minecraft.client.resources.server.ServerPackManager$ServerPackData")
public interface ServerPackDataAccessor {
    @Accessor("path")
    Path viaBedrockUtility$getPath();

    @Accessor("removalReason")
    Object viaBedrockUtility$getRemovalReason();

    @Accessor("promptAccepted")
    boolean viaBedrockUtility$getPromptAccepted();
}
