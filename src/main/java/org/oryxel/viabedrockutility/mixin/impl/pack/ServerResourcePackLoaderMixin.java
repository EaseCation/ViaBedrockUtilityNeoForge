package org.oryxel.viabedrockutility.mixin.impl.pack;

import net.easecation.bedrockmotion.pack.PackManager;
import net.easecation.bedrockmotion.pack.content.Content;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.resources.server.PackReloadConfig;
import net.minecraft.server.packs.repository.Pack;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import org.oryxel.viabedrockutility.pack.processor.TextureProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Mixin(DownloadedPackSource.class)
public class ServerResourcePackLoaderMixin {
    @Inject(method = "loadRequestedPacks", at = @At("HEAD"))
    private void loadRequestedPacks(List<PackReloadConfig.IdAndPath> packs, CallbackInfoReturnable<List<Pack>> cir) {
        if (!ViaBedrockUtility.getInstance().isViaBedrockPresent()) {
            return;
        }

        ViaBedrockUtilityNeoForge.LOGGER.debug("[ResourcePack] Intercepting server resource packs, {} pack(s) received", packs.size());
        final List<Path> packPaths = packs.stream().map(PackReloadConfig.IdAndPath::path).toList();
        loadBedrockPacks(packPaths);
    }

    private void loadBedrockPacks(List<Path> packs) {
        final List<Content> contents = new ArrayList<>();
        packs.forEach(pack -> {
            try {
                final Content content = new Content(Files.readAllBytes(pack));
                final List<String> mcpacks = content.getFilesDeep("bedrock/", ".mcpack");
                ViaBedrockUtilityNeoForge.LOGGER.debug("[ResourcePack] Found {} bedrock mcpack(s) in {}", mcpacks.size(), pack.getFileName());
                for (final String path : mcpacks) {
                    ViaBedrockUtilityNeoForge.LOGGER.debug("[ResourcePack]   - {}", path);
                    contents.add(new Content(content.get(path)));
                }
            } catch (IOException e) {
                ViaBedrockUtilityNeoForge.LOGGER.warn("[ResourcePack] Failed to read pack {}", pack);
            }
        });

        ViaBedrockUtilityNeoForge.LOGGER.info("[ResourcePack] Loaded {} bedrock pack(s) total, initializing PackManager", contents.size());

        // Load vanilla.mcpack textures as base layer (e.g. textures/particle/particles.png)
        // Without this, particle textures from vanilla.mcpack won't be in TextureManager,
        // causing a crash when BillboardParticleSubmittable tries to bind them during render pass.
        // Note: BedrockMotion's vanilla.mcpack has no textures; VBU's copy (in assets/) does.
        final List<Content> textureContents = new ArrayList<>();
        try (java.io.InputStream is = ViaBedrockUtilityNeoForge.class.getResourceAsStream("/assets/viabedrockutility/vanilla_packs/vanilla.mcpack")) {
            if (is != null) {
                textureContents.add(new Content(is.readAllBytes()));
                ViaBedrockUtilityNeoForge.LOGGER.info("[ResourcePack] Loaded vanilla.mcpack textures as base layer");
            } else {
                ViaBedrockUtilityNeoForge.LOGGER.warn("[ResourcePack] vanilla.mcpack not found in assets");
            }
        } catch (IOException e) {
            ViaBedrockUtilityNeoForge.LOGGER.warn("[ResourcePack] Failed to load vanilla.mcpack for textures", e);
        }
        textureContents.addAll(contents);
        TextureProcessor.process(textureContents);

        ViaBedrockUtility.getInstance().setPackManager(new PackManager(contents));

        // Replay any payloads (notably the initial skin overrides) that arrived before PackManager was ready.
        // Deferred to the client thread so it runs after PackManager is set and safely touches render objects.
        final var handler = ViaBedrockUtility.getInstance().getPayloadHandler();
        if (handler != null) {
            net.minecraft.client.Minecraft.getInstance().execute(handler::flushPendingPayloads);
        }

        // Load particle definitions into BEParticle
        loadParticleDefinitions(contents);
    }

    private void loadParticleDefinitions(List<Content> contents) {
        // Count particle files first to avoid clearing definitions when no packs are present (e.g. on disconnect)
        int count = 0;
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
                    ViaBedrockUtilityNeoForge.LOGGER.warn("[Particle] Failed to load particle definition: {}", path, e);
                }
            }
        }
        if (pendingDefinitions.isEmpty()) {
            ViaBedrockUtilityNeoForge.LOGGER.info("[Particle] No particle definitions found, keeping existing definitions");
            return;
        }
        net.easecation.beparticle.ParticleManager.INSTANCE.clear();
        for (final var entry : pendingDefinitions) {
            net.easecation.beparticle.ParticleManager.INSTANCE.loadDefinition(entry.getKey(), entry.getValue());
            ViaBedrockUtilityNeoForge.LOGGER.info("[Particle:L0] Loaded particle definition: {}", entry.getKey());
            count++;
        }
        ViaBedrockUtilityNeoForge.LOGGER.info("[Particle] Loaded {} particle definition(s)", count);
    }
}
