package org.oryxel.viabedrockutility.mixin.impl.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.oryxel.viabedrockutility.mixin.interfaces.IRenderTypeTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

@Mixin(targets = "net.minecraft.client.renderer.RenderType$CompositeRenderType")
public abstract class CompositeRenderTypeMixin implements IRenderTypeTexture {
    @Shadow
    @Final
    private RenderType.CompositeState state;

    @Override
    public Optional<ResourceLocation> viaBedrockUtility$getTexture() {
        return ((IRenderTypeTexture) (Object) this.state).viaBedrockUtility$getTexture();
    }
}
