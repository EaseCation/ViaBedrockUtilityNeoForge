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
import java.util.Map;

@Mixin(DownloadedPackSource.class)
public class ServerResourcePackLoaderMixin {
    @Inject(method = "loadRequestedPacks", at = @At("HEAD"))
    private void loadRequestedPacks(List<PackReloadConfig.IdAndPath> packs, CallbackInfoReturnable<List<Pack>> cir) {
        if (!ViaBedrockUtility.getInstance().isViaBedrockPresent()) {
            return;
        }

        ViaBedrockUtilityNeoForge.LOGGER.debug("[ResourcePack] Intercepting server resource packs, {} pack(s) received", packs.size());
        final List<Path> packPaths = packs.stream().map(PackReloadConfig.IdAndPath::path).toList();
        final long connectionEpoch = ViaBedrockUtility.getInstance().getConnectionEpoch();
        loadBedrockPacks(packPaths, connectionEpoch);
    }

    private void loadBedrockPacks(List<Path> packs, long connectionEpoch) {
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
        final PackManager nextManager = new PackManager(contents);
        if (!ViaBedrockUtility.getInstance().isCurrentConnectionEpoch(connectionEpoch)) {
            ViaBedrockUtilityNeoForge.LOGGER.debug(
                    "[ResourcePack] Connection changed while preparing packs; discarding generation {}",
                    connectionEpoch);
            return;
        }

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

        // Publish particle definitions before the PackManager generation. Both operations run on
        // the client reload thread; render code can therefore observe only the complete old or
        // complete new generation, never a new PackManager with stale particle definitions.
        loadParticleDefinitions(contents);
        if (!ViaBedrockUtility.getInstance().publishPackManager(nextManager, connectionEpoch)) {
            return;
        }

        // Replay any payloads (notably the initial skin overrides) that arrived before PackManager was ready.
        // Deferred to the client thread so it runs after PackManager is set and safely touches render objects.
        final var handler = ViaBedrockUtility.getInstance().getPayloadHandler();
        if (handler != null) {
            net.minecraft.client.Minecraft.getInstance().execute(handler::flushPendingPayloads);
        }

    }

    private void loadParticleDefinitions(List<Content> contents) {
        // 收集本来源（mcpack 层）的全部定义为 identifier→json；
        // 空则保留现有定义（断线/本包无粒子时不动其它来源，避免误抹）。
        final java.util.LinkedHashMap<String, String> defs = new java.util.LinkedHashMap<>();
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
                    final com.google.gson.JsonObject renderParameters = desc.getAsJsonObject("basic_render_parameters");
                    if (renderParameters != null && renderParameters.has("texture")) {
                        final String texture = renderParameters.get("texture").getAsString();
                        if (!TextureProcessor.isRegisteredTexture(texture)) {
                            ViaBedrockUtilityNeoForge.LOGGER.warn(
                                    "[Particle] Definition '{}' references missing VBU texture '{}' (source={})",
                                    identifier, texture, path);
                        }
                    }
                    defs.put(identifier, json);
                } catch (Exception e) {
                    ViaBedrockUtilityNeoForge.LOGGER.warn("[Particle] Failed to load particle definition: {}", path, e);
                }
            }
        }
        if (defs.isEmpty()) {
            ViaBedrockUtilityNeoForge.LOGGER.info("[Particle] No particle definitions found; clearing the VBU mcpack generation");
        }
        // 按来源替换：只替换本 source（mcpack 层）的定义，不再 clear 全表
        // → 与 GeyserUtilsBridge 的散文件 bedrock_pack 层共存，互不抹除。
        final net.easecation.beparticle.ParticleManager manager = net.easecation.beparticle.ParticleManager.INSTANCE;
        manager.loadDefinitions("viabedrockutility", defs);
    }
}
