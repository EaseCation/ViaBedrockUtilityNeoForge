package org.oryxel.viabedrockutility.mixin.impl.accessor;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderType.CompositeState.class)
public interface CompositeStateAccessor {
    @Accessor("outputState")
    RenderStateShard.OutputStateShard viaBedrockUtility$getOutputState();
}
