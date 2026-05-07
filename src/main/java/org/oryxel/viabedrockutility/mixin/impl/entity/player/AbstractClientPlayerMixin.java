package org.oryxel.viabedrockutility.mixin.impl.entity.player;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.payload.PayloadHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(PlayerInfo.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void injectGetSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerInfo self = (PlayerInfo) (Object) this;
        if (!ViaBedrockUtility.getInstance().isViaBedrockPresent()) {
            return;
        }

        final PayloadHandler handler = ViaBedrockUtility.getInstance().getPayloadHandler();
        final UUID uuid = self.getProfile().getId();
        final PayloadHandler.CachedPlayerSkin skinCache = handler.getCachedPlayerSkins().get(uuid);
        final ResourceLocation capeId = handler.getCachedPlayerCapes().get(uuid);
        if (skinCache == null && capeId == null) {
            return;
        }

        PlayerSkin skin = cir.getReturnValue();
        ResourceLocation texture = skin.texture();
        ResourceLocation cape = skin.capeTexture();
        PlayerSkin.Model model = skin.model();
        boolean changed = false;

        if (skinCache != null && !skinCache.getTextureId().equals(texture)) {
            texture = skinCache.getTextureId();
            model = skinCache.isSlim() ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE;
            changed = true;
        }

        if (capeId != null && !capeId.equals(cape)) {
            cape = capeId;
            changed = true;
        }

        if (changed) {
            cir.setReturnValue(new PlayerSkin(texture, skin.textureUrl(), cape, skin.elytraTexture(), model, skin.secure()));
        }
    }
}
