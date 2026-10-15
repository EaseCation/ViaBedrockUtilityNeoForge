package org.oryxel.viabedrockutility.payload.handler;

import lombok.Getter;
import net.easecation.bedrockmotion.pack.PackManager;
import org.oryxel.viabedrockutility.entity.CustomEntityTicker;
import org.oryxel.viabedrockutility.enums.bedrock.ActorFlags;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import org.oryxel.viabedrockutility.material.VanillaMaterials;
import org.oryxel.viabedrockutility.material.data.Material;
import net.easecation.bedrockmotion.pack.definitions.EntityDefinitions;
import org.oryxel.viabedrockutility.payload.PayloadHandler;
import org.oryxel.viabedrockutility.payload.impl.entity.AnimatePayload;
import org.oryxel.viabedrockutility.payload.impl.entity.ModelRequestPayload;
import org.oryxel.viabedrockutility.payload.impl.player.PlayerAnimatePayload;
import org.oryxel.viabedrockutility.renderer.CustomPlayerRenderer;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.binding.JavaObjectBinding;
import team.unnamed.mocha.runtime.standard.MochaMath;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class CustomEntityPayloadHandler extends PayloadHandler {
    public static final Scope BASE_SCOPE = Scope.create();
    public static final Material DEFAULT_MATERIAL;
    private final Map<UUID, ModelRequestState> activeModelRequests = new ConcurrentHashMap<>();

    static {
        //noinspection UnstableApiUsage
        BASE_SCOPE.set("math", JavaObjectBinding.of(MochaMath.class, null, new MochaMath()));
        BASE_SCOPE.readOnly(true);

        DEFAULT_MATERIAL = VanillaMaterials.getMaterial("entity");
    }

    @Override
    public void handle(ModelRequestPayload payload) {
        final ModelRequestState state = ModelRequestState.from(payload);
        this.activeModelRequests.put(payload.getUuid(), state);
        if (!this.packManager.getEntityDefinitions().getEntities().containsKey(payload.getIdentifier())) {
            ViaBedrockUtilityNeoForge.LOGGER.warn("[Entity] MODEL_REQUEST for unknown entity '{}' (uuid={}), skipping", payload.getIdentifier(), payload.getUuid());
            return;
        }

        final EntityDefinitions.EntityDefinition definition = this.packManager.getEntityDefinitions().getEntities().get(payload.getIdentifier());

        CustomEntityTicker ticker = this.cachedCustomEntities.get(payload.getUuid());
        if (ticker != null && !ticker.getEntityDefinition().identifier().equals(payload.getIdentifier())) {
            ticker.getRenderer().invalidateFrozenMeshes("entity_definition_changed");
            ticker = null;
        }
        if (ticker == null) {
            ticker = new CustomEntityTicker(this.packManager, definition);
            this.cachedCustomEntities.put(payload.getUuid(), ticker);
        }

        state.apply(ticker);
    }

    @Override
    public void onPackManagerChanged(PackManager manager) {
        super.onPackManagerChanged(manager);
        final Map<UUID, CustomEntityTicker> replacements = new LinkedHashMap<>();
        if (manager != null) {
            for (Map.Entry<UUID, ModelRequestState> entry : activeModelRequests.entrySet()) {
                final EntityDefinitions.EntityDefinition definition = manager.getEntityDefinitions()
                        .getEntities().get(entry.getValue().identifier());
                if (definition == null) {
                    ViaBedrockUtilityNeoForge.LOGGER.warn(
                            "[Entity] Definition '{}' disappeared after pack reload (uuid={})",
                            entry.getValue().identifier(), entry.getKey());
                    continue;
                }
                try {
                    final CustomEntityTicker ticker = new CustomEntityTicker(manager, definition);
                    entry.getValue().apply(ticker);
                    replacements.put(entry.getKey(), ticker);
                } catch (RuntimeException exception) {
                    ViaBedrockUtilityNeoForge.LOGGER.warn(
                            "[Entity] Failed to rebuild '{}' after pack reload (uuid={})",
                            entry.getValue().identifier(), entry.getKey(), exception);
                }
            }
        }
        this.cachedCustomEntities.values().forEach(ticker ->
                ticker.getRenderer().invalidateFrozenMeshes("pack_generation_changed"));
        this.cachedCustomEntities.clear();
        this.cachedCustomEntities.putAll(replacements);
    }

    @Override
    public void resetConnectionState() {
        activeModelRequests.clear();
        super.resetConnectionState();
    }

    @Override
    public void removeCustomEntity(UUID uuid) {
        activeModelRequests.remove(uuid);
        super.removeCustomEntity(uuid);
    }

    @Override
    public void handle(AnimatePayload payload) {
        if (this.packManager == null) {
            return;
        }
        // 1) Custom model entity (MODEL_REQUEST path).
        final CustomEntityTicker ticker = this.cachedCustomEntities.get(payload.getEntityUuid());
        if (ticker != null) {
            final var animData = this.packManager.getAnimationDefinitions().getAnimations().get(payload.getAnimationName());
            if (animData == null) {
                ViaBedrockUtilityNeoForge.LOGGER.debug("[Entity] ANIMATE: animation '{}' not found in PackManager (uuid={})", payload.getAnimationName(), payload.getEntityUuid());
                return;
            }
            ticker.getRenderer().playExplicit(animData);
            return;
        }

        // 旧协议没有控制器参数，使用默认运行时控制器及动画结束条件。
        final var renderer = this.cachedPlayerRenderers.get(payload.getEntityUuid());
        if (renderer instanceof CustomPlayerRenderer customPlayerRenderer) {
            playPlayerAnimation(customPlayerRenderer, payload.getEntityUuid(), payload.getAnimationName(), "default",
                    "query.any_animation_finished", "__runtime_controller", 0.0F);
            return;
        }

        ViaBedrockUtilityNeoForge.LOGGER.debug("[Entity] ANIMATE: no custom-entity/player renderer for uuid={} (animation '{}')", payload.getEntityUuid(), payload.getAnimationName());
    }

    @Override
    public void handle(PlayerAnimatePayload payload) {
        final var renderer = this.cachedPlayerRenderers.get(payload.getPlayerUuid());
        if (renderer instanceof CustomPlayerRenderer customPlayerRenderer) {
            playPlayerAnimation(customPlayerRenderer, payload.getPlayerUuid(), payload.getAnimation(), payload.getNextState(),
                    payload.getStopExpression(), payload.getController(), payload.getBlendOutTime());
        } else {
            ViaBedrockUtilityNeoForge.LOGGER.debug(
                    "[Entity] PLAYER_ANIMATE: no player renderer for uuid={} (animation '{}')",
                    payload.getPlayerUuid(), payload.getAnimation());
        }
    }

    private void playPlayerAnimation(CustomPlayerRenderer renderer, UUID playerUuid, String animation,
                                     String nextState, String stopExpression, String controller,
                                     float blendOutTime) {
        try {
            renderer.playAnimation(animation, nextState, stopExpression, controller, blendOutTime);
        } catch (IllegalArgumentException exception) {
            // 校验在入队之前完成；无效指令保留原状态并报告资源或表达式错误。
            ViaBedrockUtilityNeoForge.LOGGER.warn(
                    "[Entity] Invalid player animation '{}' for uuid={} (controller '{}')",
                    animation, playerUuid, controller, exception);
        }
    }

    private record ModelRequestState(String identifier, Set<ActorFlags> flags, Integer variant,
                                     Integer markVariant, Integer skinId, Float scale) {
        static ModelRequestState from(ModelRequestPayload payload) {
            final Set<ActorFlags> source = payload.getEntityData().flags();
            final Set<ActorFlags> flags = source == null || source.isEmpty()
                    ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(source));
            return new ModelRequestState(payload.getIdentifier(), flags,
                    payload.getEntityData().variant(), payload.getEntityData().mark_variant(),
                    payload.getEntityData().skinId(), payload.getEntityData().scale());
        }

        void apply(CustomEntityTicker ticker) {
            ticker.setEntityFlags(flags);
            ticker.updateRenderQueries(variant, markVariant, skinId);
            ticker.setScale(scale);
            ticker.update();
        }
    }
}
