package org.oryxel.viabedrockutility.util;

import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

public final class PlayerSkinBuilder {
    public ResourceLocation texture;
    public String textureUrl;
    public ResourceLocation capeTexture;
    public ResourceLocation elytraTexture;
    public PlayerSkin.Model model;
    public boolean secure;

    public PlayerSkinBuilder(final PlayerSkin base) {
        this.texture = base.texture();
        this.textureUrl = base.textureUrl();
        this.capeTexture = base.capeTexture();
        this.elytraTexture = base.elytraTexture();
        this.model = base.model();
        this.secure = base.secure();
    }

    public PlayerSkin build() {
        return new PlayerSkin(texture, textureUrl, capeTexture, elytraTexture, model, secure);
    }
}
