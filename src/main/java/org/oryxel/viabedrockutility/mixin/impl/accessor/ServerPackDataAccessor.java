package org.oryxel.viabedrockutility.mixin.impl.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.nio.file.Path;

@Mixin(targets = "net.minecraft.client.resources.server.ServerPackManager$ServerPackData")
public interface ServerPackDataAccessor {
    @Accessor("path")
    Path viaBedrockUtility$getPath();

    @Invoker("isRemoved")
    boolean viaBedrockUtility$isRemoved();

    @Accessor("promptAccepted")
    boolean viaBedrockUtility$getPromptAccepted();
}
