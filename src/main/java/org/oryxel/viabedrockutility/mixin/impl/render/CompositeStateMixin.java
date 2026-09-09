package org.oryxel.viabedrockutility.mixin.impl.render;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.oryxel.viabedrockutility.mixin.interfaces.IRenderTypeTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

@Mixin(RenderType.CompositeState.class)
public abstract class CompositeStateMixin implements IRenderTypeTexture {
    @Shadow
    @Final
    private RenderStateShard.EmptyTextureStateShard textureState;

    @Override
    public Optional<ResourceLocation> viaBedrockUtility$getTexture() {
        return this.textureState instanceof IRenderTypeTexture textureProvider
                ? textureProvider.viaBedrockUtility$getTexture()
                : Optional.empty();
    }
}
