package org.oryxel.viabedrockutility.mixin.impl.render;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.resources.ResourceLocation;
import org.oryxel.viabedrockutility.mixin.interfaces.IRenderTypeTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

@Mixin(RenderStateShard.TextureStateShard.class)
public abstract class TextureStateShardMixin implements IRenderTypeTexture {
    @Shadow
    @Final
    private Optional<ResourceLocation> texture;

    @Override
    public Optional<ResourceLocation> viaBedrockUtility$getTexture() {
        return this.texture;
    }
}
