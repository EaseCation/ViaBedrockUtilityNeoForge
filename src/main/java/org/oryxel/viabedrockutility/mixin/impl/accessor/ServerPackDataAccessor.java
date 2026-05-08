package org.oryxel.viabedrockutility.mixin.impl.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.nio.file.Path;
import java.util.UUID;

@Mixin(targets = "net.minecraft.client.resources.server.ServerPackManager$ServerPackData")
public interface ServerPackDataAccessor {
    @Accessor("id")
    UUID viaBedrockUtility$getId();

    @Accessor("path")
    Path viaBedrockUtility$getPath();

    @Invoker("isRemoved")
    boolean viaBedrockUtility$isRemoved();

    @Accessor("promptAccepted")
    boolean viaBedrockUtility$getPromptAccepted();
}
