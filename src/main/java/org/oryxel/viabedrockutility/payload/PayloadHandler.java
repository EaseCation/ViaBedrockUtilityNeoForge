package org.oryxel.viabedrockutility.payload;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.entity.CustomEntityTicker;
import org.oryxel.viabedrockutility.fabric.ViaBedrockUtilityFabric;
import org.oryxel.viabedrockutility.mixin.impl.accessor.PlayerSkinFieldAccessor;
import net.easecation.bedrockmotion.pack.PackManager;
import org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler;
import org.oryxel.viabedrockutility.payload.impl.entity.ModelRequestPayload;
import org.oryxel.viabedrockutility.payload.impl.skin.BaseSkinPayload;
import org.oryxel.viabedrockutility.payload.impl.skin.CapeDataPayload;
import org.oryxel.viabedrockutility.payload.impl.skin.SkinAnimationDataPayload;
import org.oryxel.viabedrockutility.payload.impl.skin.SkinAnimationInfoPayload;
import org.oryxel.viabedrockutility.payload.impl.skin.SkinDataPayload;
import org.oryxel.viabedrockutility.payload.impl.particle.SpawnParticlePayload;
import org.oryxel.viabedrockutility.animation.PlayerAnimationManager;
import org.oryxel.viabedrockutility.mixin.interfaces.IBedrockAnimatedModel;
import net.easecation.bedrockmotion.pack.definitions.AnimationDefinitions;
import org.oryxel.viabedrockutility.renderer.AnimatedSkinOverlay;
import org.oryxel.viabedrockutility.renderer.CustomPlayerRenderer;
import org.oryxel.viabedrockutility.util.GeometryUtil;

import org.oryxel.viabedrockutility.util.ImageUtil;
import org.oryxel.viabedrockutility.util.PlayerSkinBuilder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class PayloadHandler {
    protected final Map<UUID, CustomEntityTicker> cachedCustomEntities = new ConcurrentHashMap<>();
    protected final Map<UUID, EntityRenderer<?, ?>> cachedPlayerRenderers = new ConcurrentHashMap<>();
    protected final Map<UUID, ResourceLocation> cachedPlayerCapes = new ConcurrentHashMap<>();
    protected final Map<UUID, SkinInfo> cachedSkinInfo = new ConcurrentHashMap<>();
    protected final Map<UUID, CachedPlayerSkin> cachedPlayerSkins = new ConcurrentHashMap<>();
    protected final Map<UUID, Map<Integer, PendingAnimation>> pendingAnimations = new ConcurrentHashMap<>();
    protected PackManager packManager;

    public void handle(final BasePayload payload) {
        if (this.packManager != ViaBedrockUtility.getInstance().getPackManager()) {
            this.packManager = ViaBedrockUtility.getInstance().getPackManager();
        }

        if (this.packManager == null) {
            ViaBedrockUtilityFabric.LOGGER.warn("[Payload] Received {} but PackManager is null, ignoring", payload.getClass().getSimpleName());
            return;
        }

        if (payload instanceof ModelRequestPayload modelRequest) {
            this.handle(modelRequest);
        } else if (payload instanceof BaseSkinPayload baseSkin) {
            ViaBedrockUtilityFabric.LOGGER.debug("[Skin] Received skin info for player {} ({}x{}, {} chunk(s))", baseSkin.getPlayerUuid(), baseSkin.getSkinWidth(), baseSkin.getSkinHeight(), baseSkin.getChunkCount());
            this.cachedSkinInfo.put(baseSkin.getPlayerUuid(), new SkinInfo(baseSkin.getGeometry(), baseSkin.getResourcePatch(), baseSkin.getSkinWidth(), baseSkin.getSkinHeight(), baseSkin.getChunkCount()));
        } else if (payload instanceof SkinDataPayload skinData) {
            this.handle(skinData);
        } else if (payload instanceof CapeDataPayload capePayload) {
            ViaBedrockUtilityFabric.LOGGER.debug("[Skin] Received cape data for player {}", capePayload.getPlayerUuid());
            this.handle(capePayload);
        } else if (payload instanceof SkinAnimationInfoPayload animInfo) {
            this.handle(animInfo);
        } else if (payload instanceof SkinAnimationDataPayload animData) {
            this.handle(animData);
        } else if (payload instanceof SpawnParticlePayload particlePayload) {
            this.handle(particlePayload);
        }
    }

    public void handle(final ModelRequestPayload payload) {}

    public void handle(final SpawnParticlePayload payload) {
        ViaBedrockUtilityFabric.LOGGER.info("[Particle:L4] Handling SpawnParticlePayload: {} at ({}, {}, {}), molang={}", payload.getIdentifier(), payload.getX(), payload.getY(), payload.getZ(), payload.getMolangVarsJson());
        Map<String, Float> molangVars = null;
        final String json = payload.getMolangVarsJson();
        if (json != null && !json.isEmpty()) {
            molangVars = parseMolangVarsJson(json);
        }
        final var emitter = net.easecation.beparticle.ParticleManager.INSTANCE.spawnEmitter(
                payload.getIdentifier(),
                new org.joml.Vector3f(payload.getX(), payload.getY(), payload.getZ()),
                molangVars
        );
        ViaBedrockUtilityFabric.LOGGER.info("[Particle:L4] spawnEmitter result: {} (definitions loaded: {})", emitter != null ? "SUCCESS" : "NULL (definition not found)", net.easecation.beparticle.ParticleManager.INSTANCE.getDefinitionCount());
    }

    /**
     * Parse Bedrock molang variables JSON.
     * Format: [{"name":"variable.color_r","value":{"type":"float","value":1.0}}, ...]
     * Returns a map with variable names stripped of "variable." prefix (e.g. "color_r" -> 1.0f).
     */
    private Map<String, Float> parseMolangVarsJson(String json) {
        try {
            final com.google.gson.JsonElement element = com.google.gson.JsonParser.parseString(json);
            final Map<String, Float> vars = new java.util.HashMap<>();
            if (element.isJsonArray()) {
                // Bedrock format: [{"name":"variable.xxx","value":{"type":"float","value":1.0}}, ...]
                for (final com.google.gson.JsonElement item : element.getAsJsonArray()) {
                    final com.google.gson.JsonObject obj = item.getAsJsonObject();
                    String name = obj.get("name").getAsString();
                    // Strip "variable." prefix for MoLang scope binding
                    if (name.startsWith("variable.")) {
                        name = name.substring("variable.".length());
                    }
                    final com.google.gson.JsonObject valueObj = obj.getAsJsonObject("value");
                    final String type = valueObj.get("type").getAsString();
                    if ("float".equals(type)) {
                        vars.put(name, valueObj.get("value").getAsFloat());
                    }
                    // member_array type is nested, skip for now
                }
            } else if (element.isJsonObject()) {
                // Simple format fallback: {"varName": floatValue, ...}
                for (var entry : element.getAsJsonObject().entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        vars.put(entry.getKey(), entry.getValue().getAsFloat());
                    }
                }
            }
            return vars.isEmpty() ? null : vars;
        } catch (Exception e) {
            ViaBedrockUtilityFabric.LOGGER.warn("[Particle] Failed to parse molang vars JSON: {}", json, e);
            return null;
        }
    }

    public void handle(final CapeDataPayload payload) {
        final NativeImage capeImage = ImageUtil.toNativeImage(payload.getCapeData(), payload.getWidth(), payload.getHeight());
        if (capeImage == null) {
            return;
        }

        final Minecraft client = Minecraft.getInstance();
        client.getTextureManager().register(payload.getIdentifier(), new DynamicTexture(() -> payload.getIdentifier().toString() + capeImage.hashCode() , capeImage));

        if (client.getConnection() == null) {
            return;
        }

        this.cachedPlayerCapes.put(payload.getPlayerUuid(), payload.getIdentifier());

        // It's ok to use this here, the reason we don't use this for player geometry because there can be fake entity.
        // But most fake entity don't have cape so we should be fine!
        final PlayerInfo entry = client.getConnection().getPlayerInfo(payload.getPlayerUuid());
        if (entry == null) {
            return;
        }

        final PlayerSkinBuilder builder = new PlayerSkinBuilder(entry.getSkin());
        builder.capeTexture = payload.getIdentifier();

        ((PlayerSkinFieldAccessor)entry).setPlayerSkin(builder::build);
    }

    private static final List<String> HARDCODED_GEOMETRY_IDENTIFIERS = List.of(
            "geometry.humanoid.custom", "geometry.humanoid.customSlim");

    public void handle(final SkinDataPayload payload) {
        final SkinInfo info = this.cachedSkinInfo.get(payload.getPlayerUuid());
        if (info == null) {
            ViaBedrockUtilityFabric.LOGGER.error("Skin info was null!");
            return;
        }

        info.setData(payload.getSkinData(), payload.getChunkPosition());
        ViaBedrockUtilityFabric.LOGGER.debug("Skin chunk {} received for {}", payload.getChunkPosition(), payload.getPlayerUuid());

        if (info.isComplete()) {
            // All skin data has been received
            this.cachedSkinInfo.remove(payload.getPlayerUuid());
        } else {
            return;
        }

        final NativeImage skinImage = ImageUtil.toNativeImage(info.getData(), info.getWidth(), info.getHeight());
        if (skinImage == null) {
            ViaBedrockUtilityFabric.LOGGER.error("[Skin] toNativeImage returned null for {}", payload.getPlayerUuid());
            return;
        }
        ViaBedrockUtilityFabric.LOGGER.debug("[Skin] NativeImage created for {} ({}x{})", payload.getPlayerUuid(), info.getWidth(), info.getHeight());

        final Minecraft client = Minecraft.getInstance();

        final ResourceLocation identifier = ResourceLocation.fromNamespaceAndPath(ViaBedrockUtilityFabric.MOD_ID, payload.getPlayerUuid().toString());
        client.getTextureManager().register(identifier, new DynamicTexture(() -> identifier.toString() + skinImage.hashCode(), skinImage));
        ViaBedrockUtilityFabric.LOGGER.debug("[Skin] Texture registered: {}", identifier);

        if (client.getConnection() != null) {
            final PlayerInfo entry = client.getConnection().getPlayerInfo(payload.getPlayerUuid());
            ViaBedrockUtilityFabric.LOGGER.debug("[Skin] PlayerInfo lookup for {}: {}", payload.getPlayerUuid(), entry != null ? entry.getProfile().getName() : "NOT FOUND");

            // If we can still get player list entry then use this to set skin still a good idea!
            if (entry != null) {
                final PlayerSkinBuilder builder = new PlayerSkinBuilder(entry.getSkin());
                builder.texture = identifier;

                ((PlayerSkinFieldAccessor)entry).setPlayerSkin(builder::build);
            }
        } else {
            ViaBedrockUtilityFabric.LOGGER.warn("[Skin] NetworkHandler is null!");
        }

        // Ex: skinResourcePatch={"geometry":{"default":"geometry.humanoid.custom.1742391406.1704"}}
        String requiredGeometry = null;
        try {
            requiredGeometry = JsonParser.parseString(info.getResourcePatch()).getAsJsonObject()
                    .getAsJsonObject("geometry").get("default").getAsString();
        } catch (Exception ignored) {}
        ViaBedrockUtilityFabric.LOGGER.debug("[Skin] requiredGeometry={} resourcePatch={}", requiredGeometry, info.getResourcePatch());

        // Hardcoded I know...
        boolean slim = requiredGeometry != null && requiredGeometry.startsWith("geometry.humanoid.customSlim");

        PlayerModel model = null;
        if (!info.getGeometryRaw().isEmpty()) {
            final List<BedrockGeometryModel> geometries;
            try {
                final JsonObject object = JsonParser.parseString(info.getGeometryRaw()).getAsJsonObject();
                geometries = BedrockGeometryModel.fromJson(object);

                if (!geometries.isEmpty()) {
                    BedrockGeometryModel geometry = geometries.getFirst();
                    if (requiredGeometry != null) {
                        for (final BedrockGeometryModel geometryModel : geometries) {
                            if (geometryModel.getIdentifier().equals(requiredGeometry)) {
                                geometry = geometryModel;
                                break;
                            }
                        }
                    }

                    model = (PlayerModel) GeometryUtil.buildModel(geometry, true, slim);
                    ViaBedrockUtilityFabric.LOGGER.debug("[Skin] Built custom geometry model for {}", payload.getPlayerUuid());
                }
            } catch (final Exception e) {
                ViaBedrockUtilityFabric.LOGGER.error("[Skin] Failed to parse geometry for {}: {}", payload.getPlayerUuid(), e.getMessage());
            }
        }

        if (model == null) {
            if (requiredGeometry == null) {
                ViaBedrockUtilityFabric.LOGGER.warn("[Skin] requiredGeometry is null, returning early for {}", payload.getPlayerUuid());
                return;
            }

            boolean found = false;

            for (final String i : HARDCODED_GEOMETRY_IDENTIFIERS) {
                if (i.equals(requiredGeometry) || requiredGeometry.startsWith(i + ".")) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                ViaBedrockUtilityFabric.LOGGER.warn("[Skin] Geometry '{}' not in hardcoded list, returning early for {}", requiredGeometry, payload.getPlayerUuid());
                return;
            }
        }

        if (model == null) {
            // Classic skins use the vanilla 64x64 model layout here.
            model = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot(), slim);
            ViaBedrockUtilityFabric.LOGGER.debug("[Skin] Using default player model (slim={}) for {}", slim, payload.getPlayerUuid());
        }

        final EntityRendererProvider.Context entityContext = new EntityRendererProvider.Context(client.getEntityRenderDispatcher(),
                client.getItemModelResolver(), client.getMapRenderer(), client.getBlockRenderer(),
                client.getResourceManager(), client.getEntityModels(), new EquipmentAssetManager(), client.font);
        this.cachedPlayerRenderers.put(payload.getPlayerUuid(), new CustomPlayerRenderer(entityContext, model, slim, identifier));
        this.cachedPlayerSkins.put(payload.getPlayerUuid(), new CachedPlayerSkin(identifier, slim, info.getGeometryRaw(), info.getResourcePatch()));
        ViaBedrockUtilityFabric.LOGGER.debug("[Skin] CustomPlayerRenderer created for {}", payload.getPlayerUuid());

        // Parse animation overrides from skinResourcePatch.animations
        if (this.packManager != null) {
            try {
                final JsonObject patch = JsonParser.parseString(info.getResourcePatch()).getAsJsonObject();
                if (patch.has("animations")) {
                    final JsonObject anims = patch.getAsJsonObject("animations");
                    final PlayerAnimationManager animManager = new PlayerAnimationManager();
                    for (final var animEntry : anims.entrySet()) {
                        final String animIdentifier = animEntry.getValue().getAsString();
                        final AnimationDefinitions.AnimationData animData =
                                this.packManager.getAnimationDefinitions().getAnimations().get(animIdentifier);
                        if (animData != null) {
                            animManager.addAnimation(animEntry.getKey(), animData);
                        } else {
                            ViaBedrockUtilityFabric.LOGGER.warn("[Skin] Animation '{}' ({}) not found in PackManager for {}",
                                    animEntry.getKey(), animIdentifier, payload.getPlayerUuid());
                        }
                    }
                    if (!animManager.isEmpty()) {
                        ((IBedrockAnimatedModel) (Object) model).viaBedrockUtility$setAnimationManager(animManager);
                        ViaBedrockUtilityFabric.LOGGER.debug("[Skin] Loaded {} animation overrides for {}",
                                animManager.getAffectedBones().size(), payload.getPlayerUuid());
                    }
                }
            } catch (final Exception e) {
                ViaBedrockUtilityFabric.LOGGER.warn("[Skin] Failed to parse animation overrides for {}: {}",
                        payload.getPlayerUuid(), e.getMessage());
            }
        }

        if (client.getConnection() == null) {
            return;
        }

        final PlayerInfo entry = client.getConnection().getPlayerInfo(payload.getPlayerUuid());

        // Do this once again for emmmm the slim or wide model.
        if (entry != null) {
            final PlayerSkinBuilder builder = new PlayerSkinBuilder(entry.getSkin());
            builder.texture = identifier;
            builder.model = slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE;

            ((PlayerSkinFieldAccessor)entry).setPlayerSkin(builder::build);
            ViaBedrockUtilityFabric.LOGGER.debug("[Skin] Final skin applied to PlayerInfo for {}", payload.getPlayerUuid());
        } else {
            ViaBedrockUtilityFabric.LOGGER.warn("[Skin] Final PlayerInfo NOT FOUND for {}", payload.getPlayerUuid());
        }
    }

    @Getter
    public static class SkinInfo {
        private final String geometryRaw, resourcePatch;
        private final int width, height;
        private final byte[][] skinData;

        public SkinInfo(String geometryRaw, String resourcePatch, int width, int height, int chunkCount) {
            this.geometryRaw = geometryRaw;
            this.resourcePatch = resourcePatch;
            this.skinData = new byte[chunkCount][];
            this.width = width;
            this.height = height;
        }
        /**
         * Should the skin data be sent to us through multiple plugin messages, assemble it.
         */
        public byte[] getData() {
            if (skinData.length == 1) {
                // No concatenation needed
                return skinData[0];
            }

            int totalLength = 0;
            for (byte[] data : skinData) {
                totalLength += data.length;
            }
            byte[] totalData = new byte[totalLength];
            int currentIndex = 0;
            for (byte[] currentData : skinData) {
                // Copy all arrays to one array
                System.arraycopy(currentData, 0, totalData, currentIndex, currentData.length);
                currentIndex += currentData.length;
            }
            return totalData;
        }

        public void setData(byte[] data, int chunk) {
            this.skinData[chunk] = data;
        }

        public boolean isComplete() {
            for (byte[] data : skinData) {
                if (data == null) {
                    return false;
                }
            }
            return true;
        }
    }

    public void removePlayerCache(UUID uuid) {
        cachedPlayerRenderers.remove(uuid);
        cachedPlayerCapes.remove(uuid);
        cachedPlayerSkins.remove(uuid);
        cachedSkinInfo.remove(uuid);
        pendingAnimations.remove(uuid);
    }

    @Getter
    public static class CachedPlayerSkin {
        private final ResourceLocation textureId;
        private final boolean slim;
        private final String geometryRaw;
        private final String resourcePatch;

        public CachedPlayerSkin(ResourceLocation textureId, boolean slim, String geometryRaw, String resourcePatch) {
            this.textureId = textureId;
            this.slim = slim;
            this.geometryRaw = geometryRaw;
            this.resourcePatch = resourcePatch;
        }
    }

    public void handle(final SkinAnimationInfoPayload payload) {
        ViaBedrockUtilityFabric.LOGGER.debug("[Skin] Received animation info: uuid={} index={} type={} frames={} {}x{} chunks={}",
                payload.getPlayerUuid(), payload.getAnimIndex(), payload.getType(),
                payload.getFrames(), payload.getWidth(), payload.getHeight(), payload.getChunkCount());

        final PendingAnimation pending = new PendingAnimation(
                payload.getAnimIndex(), payload.getType(),
                (int) payload.getFrames(), payload.getExpression(),
                payload.getWidth(), payload.getHeight(),
                payload.getChunkCount()
        );

        pendingAnimations
                .computeIfAbsent(payload.getPlayerUuid(), k -> new ConcurrentHashMap<>())
                .put(payload.getAnimIndex(), pending);
    }

    public void handle(final SkinAnimationDataPayload payload) {
        final Map<Integer, PendingAnimation> anims = pendingAnimations.get(payload.getPlayerUuid());
        if (anims == null) return;

        final PendingAnimation pending = anims.get(payload.getAnimIndex());
        if (pending == null) return;

        pending.setData(payload.getData(), payload.getChunkPosition());
        ViaBedrockUtilityFabric.LOGGER.debug("[Skin] Animation data chunk {} received for {} index={}",
                payload.getChunkPosition(), payload.getPlayerUuid(), payload.getAnimIndex());

        if (pending.isComplete()) {
            anims.remove(payload.getAnimIndex());
            if (anims.isEmpty()) {
                pendingAnimations.remove(payload.getPlayerUuid());
            }
            buildAnimationOverlay(payload.getPlayerUuid(), pending);
        }
    }

    private void buildAnimationOverlay(UUID playerUuid, PendingAnimation pending) {
        final CachedPlayerSkin cachedSkin = cachedPlayerSkins.get(playerUuid);
        if (cachedSkin == null) {
            ViaBedrockUtilityFabric.LOGGER.warn("[Skin] No cached skin for animation overlay, uuid={}", playerUuid);
            return;
        }

        final EntityRenderer<?, ?> renderer = cachedPlayerRenderers.get(playerUuid);
        if (!(renderer instanceof CustomPlayerRenderer customRenderer)) {
            ViaBedrockUtilityFabric.LOGGER.warn("[Skin] No CustomPlayerRenderer for animation overlay, uuid={}", playerUuid);
            return;
        }

        final String geometryKey = switch (pending.type) {
            case 1 -> "animated_face";
            case 2 -> "animated_32x32";
            case 3 -> "animated_128x128";
            default -> null;
        };
        if (geometryKey == null) {
            ViaBedrockUtilityFabric.LOGGER.warn("[Skin] Unknown animation type {} for {}", pending.type, playerUuid);
            return;
        }

        // Look up geometry identifier from skinResourcePatch
        String geometryIdentifier = null;
        try {
            final JsonObject patch = JsonParser.parseString(cachedSkin.getResourcePatch()).getAsJsonObject();
            final JsonObject geometryObj = patch.getAsJsonObject("geometry");
            if (geometryObj != null && geometryObj.has(geometryKey)) {
                geometryIdentifier = geometryObj.get(geometryKey).getAsString();
            }
        } catch (Exception e) {
            ViaBedrockUtilityFabric.LOGGER.error("[Skin] Failed to parse resourcePatch for animation: {}", e.getMessage());
            return;
        }

        if (geometryIdentifier == null) {
            ViaBedrockUtilityFabric.LOGGER.warn("[Skin] No geometry identifier for key '{}' in resourcePatch for {}", geometryKey, playerUuid);
            return;
        }

        if (cachedSkin.getGeometryRaw() == null || cachedSkin.getGeometryRaw().isEmpty()) {
            ViaBedrockUtilityFabric.LOGGER.warn("[Skin] No geometryData available for animation overlay, uuid={}", playerUuid);
            return;
        }

        // Find the BedrockGeometryModel for this animation overlay
        BedrockGeometryModel targetGeometry = null;
        try {
            final JsonObject geoObj = JsonParser.parseString(cachedSkin.getGeometryRaw()).getAsJsonObject();
            final List<BedrockGeometryModel> geometries = BedrockGeometryModel.fromJson(geoObj);
            for (BedrockGeometryModel geo : geometries) {
                if (geo.getIdentifier().equals(geometryIdentifier)) {
                    targetGeometry = geo;
                    break;
                }
            }
        } catch (Exception e) {
            ViaBedrockUtilityFabric.LOGGER.error("[Skin] Failed to parse geometry for animation overlay: {}", e.getMessage());
            return;
        }

        if (targetGeometry == null) {
            ViaBedrockUtilityFabric.LOGGER.warn("[Skin] Geometry '{}' not found in geometryData for {}", geometryIdentifier, playerUuid);
            return;
        }

        // Build the overlay model
        final PlayerModel overlayModel;
        try {
            overlayModel = (PlayerModel) GeometryUtil.buildModel(targetGeometry, true, cachedSkin.isSlim(), geometryIdentifier);
        } catch (Exception e) {
            ViaBedrockUtilityFabric.LOGGER.error("[Skin] Failed to build overlay model '{}' for {}: {}", geometryIdentifier, playerUuid, e.getMessage());
            return;
        }

        // Register the sprite sheet texture
        final NativeImage textureImage = ImageUtil.toNativeImage(pending.getData(), pending.width, pending.height);
        if (textureImage == null) {
            ViaBedrockUtilityFabric.LOGGER.error("[Skin] Failed to create NativeImage for animation overlay, uuid={}", playerUuid);
            return;
        }

        final ResourceLocation textureId = ResourceLocation.fromNamespaceAndPath(ViaBedrockUtilityFabric.MOD_ID, playerUuid.toString() + "/anim_" + pending.animIndex);
        final Minecraft client = Minecraft.getInstance();
        client.getTextureManager().register(textureId, new DynamicTexture(() -> textureId.toString() + textureImage.hashCode(), textureImage));

        // Create and attach the overlay
        final int totalFrames = pending.totalFrames;
        final int frameHeight = pending.height / totalFrames;
        final AnimatedSkinOverlay overlay = new AnimatedSkinOverlay(overlayModel, textureId, pending.type, totalFrames, pending.expression, pending.height, frameHeight);
        customRenderer.addOverlay(overlay);

        ViaBedrockUtilityFabric.LOGGER.info("[Skin] Animation overlay created: uuid={} type={} frames={} geometry='{}'",
                playerUuid, pending.type, totalFrames, geometryIdentifier);
    }

    public void tickAnimationOverlays() {
        for (EntityRenderer<?, ?> renderer : cachedPlayerRenderers.values()) {
            if (renderer instanceof CustomPlayerRenderer customRenderer) {
                customRenderer.tickOverlays();
            }
        }
    }

    @Getter
    private static class PendingAnimation {
        private final int animIndex;
        private final int type;
        private final int totalFrames;
        private final int expression;
        private final int width;
        private final int height;
        private final byte[][] data;

        PendingAnimation(int animIndex, int type, int totalFrames, int expression, int width, int height, int chunkCount) {
            this.animIndex = animIndex;
            this.type = type;
            this.totalFrames = totalFrames;
            this.expression = expression;
            this.width = width;
            this.height = height;
            this.data = new byte[chunkCount][];
        }

        public void setData(byte[] chunkData, int chunkPosition) {
            this.data[chunkPosition] = chunkData;
        }

        public boolean isComplete() {
            for (byte[] chunk : data) {
                if (chunk == null) return false;
            }
            return true;
        }

        public byte[] getData() {
            if (data.length == 1) return data[0];
            int totalLength = 0;
            for (byte[] chunk : data) totalLength += chunk.length;
            byte[] result = new byte[totalLength];
            int offset = 0;
            for (byte[] chunk : data) {
                System.arraycopy(chunk, 0, result, offset, chunk.length);
                offset += chunk.length;
            }
            return result;
        }
    }
}
