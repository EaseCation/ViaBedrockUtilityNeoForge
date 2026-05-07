package org.oryxel.viabedrockutility.mixin.impl.pack;

import net.easecation.bedrockmotion.pack.PackManager;
import net.easecation.bedrockmotion.pack.content.Content;
import net.minecraft.client.resources.server.ServerPackManager;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.fabric.ViaBedrockUtilityFabric;
import org.oryxel.viabedrockutility.pack.processor.TextureProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Mixin(ServerPackManager.class)
public class ServerResourcePackLoaderMixin {
    @Inject(method = "pushLocalPack", at = @At("HEAD"))
    private void pushLocalPack(java.util.UUID id, Path pack, CallbackInfo ci) {
        if (!ViaBedrockUtility.getInstance().isViaBedrockPresent()) {
            return;
        }
        loadBedrockPacks(pack);
    }

    private void loadBedrockPacks(Path pack) {
        final List<Content> contents = new ArrayList<>();
        try {
            final Content content = new Content(Files.readAllBytes(pack));
            final List<String> mcpacks = content.getFilesDeep("bedrock/", ".mcpack");
            for (final String path : mcpacks) {
                contents.add(new Content(content.get(path)));
            }
        } catch (IOException e) {
            ViaBedrockUtilityFabric.LOGGER.warn("[ResourcePack] Failed to read pack {}", pack, e);
        }

        final List<Content> textureContents = new ArrayList<>();
        try (java.io.InputStream is = ViaBedrockUtilityFabric.class.getResourceAsStream("/assets/viabedrockutility/vanilla_packs/vanilla.mcpack")) {
            if (is != null) {
                textureContents.add(new Content(is.readAllBytes()));
            }
        } catch (IOException e) {
            ViaBedrockUtilityFabric.LOGGER.warn("[ResourcePack] Failed to load vanilla.mcpack for textures", e);
        }
        textureContents.addAll(contents);
        TextureProcessor.process(textureContents);
        ViaBedrockUtility.getInstance().setPackManager(new PackManager(contents));
        loadParticleDefinitions(contents);
    }

    private void loadParticleDefinitions(List<Content> contents) {
        java.util.List<java.util.Map.Entry<String, String>> pendingDefinitions = new java.util.ArrayList<>();
        for (final Content content : contents) {
            for (final String path : content.getFilesDeep("particles/", ".json")) {
                try {
                    final String json = content.getString(path);
                    final com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                    final com.google.gson.JsonObject effect = root.getAsJsonObject("particle_effect");
                    if (effect == null) continue;
                    final com.google.gson.JsonObject desc = effect.getAsJsonObject("description");
                    if (desc == null) continue;
                    final String identifier = desc.get("identifier").getAsString();
                    pendingDefinitions.add(java.util.Map.entry(identifier, json));
                } catch (Exception e) {
                    ViaBedrockUtilityFabric.LOGGER.warn("[Particle] Failed to load particle definition: {}", path, e);
                }
            }
        }
        if (pendingDefinitions.isEmpty()) {
            return;
        }
        net.easecation.beparticle.ParticleManager.INSTANCE.clear();
        for (final var entry : pendingDefinitions) {
            net.easecation.beparticle.ParticleManager.INSTANCE.loadDefinition(entry.getKey(), entry.getValue());
        }
    }
}
