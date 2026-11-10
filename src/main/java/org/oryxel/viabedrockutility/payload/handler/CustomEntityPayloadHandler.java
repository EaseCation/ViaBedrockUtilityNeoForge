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
import org.oryxel.viabedrockutility.payload.BasePayload;
import org.oryxel.viabedrockutility.payload.impl.entity.AnimatePayload;
import org.oryxel.viabedrockutility.payload.impl.entity.ModelRequestPayload;
import org.oryxel.viabedrockutility.payload.impl.player.PlayerAnimatePayload;
import org.oryxel.viabedrockutility.renderer.CustomPlayerRenderer;
import team.unnamed.mocha.runtime.Scope;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

@Getter
public class CustomEntityPayloadHandler extends PayloadHandler {
    public static final Scope BASE_SCOPE = net.easecation.bedrockmotion.mocha.MoLangEngine.newScope();
    public static final Material DEFAULT_MATERIAL;
    static final int MAX_OPERATIONS_PER_FRAME = 4;
    static final long FRAME_BUDGET_NANOS = 2_000_000L;
    private final ModelRequestQueue<ModelRequestState, String> modelRequests = new ModelRequestQueue<>(2048, 4096, 8);
    private long invalidModelRequests;
    private long unknownModelRequests;
    private String lastModelFailure = "";

    static {
        BASE_SCOPE.readOnly(true);

        DEFAULT_MATERIAL = VanillaMaterials.getMaterial("entity");
    }

    @Override
    public void handle(BasePayload payload) {
        // Model snapshots must be bounded even while the resource pack is unavailable.
        if (payload instanceof ModelRequestPayload model) {
            handle(model);
        } else if (payload instanceof AnimatePayload animation && modelRequests.contains(animation.getEntityUuid())) {
            handle(animation);
        } else {
            super.handle(payload);
        }
    }

    @Override
    public void handle(ModelRequestPayload payload) {
        var attributionProfiler = net.minecraft.util.profiling.Profiler.get();
        attributionProfiler.push("ec:viabedrockutility/model/request");
        try {
        if (payload.getUuid() == null || payload.getIdentifier() == null || payload.getIdentifier().length() > 1024) {
            invalidModelRequests++;
            return;
        }
        modelRequests.model(payload.getUuid(), ModelRequestState.from(payload));

        } finally { attributionProfiler.pop(); }
    }

    @Override
    public void onPackManagerChanged(PackManager manager) {
        var attributionProfiler = net.minecraft.util.profiling.Profiler.get();
        attributionProfiler.push("ec:viabedrockutility/model/resource_rebuild");
        try {
        boolean replacingGeneration = this.packManager != null;
        super.onPackManagerChanged(manager);
        this.cachedCustomEntities.values().forEach(ticker ->
                ticker.getRenderer().invalidateFrozenMeshes("pack_generation_changed"));
        this.cachedCustomEntities.clear();
        if (replacingGeneration) modelRequests.rebuild();

        } finally { attributionProfiler.pop(); }
    }

    @Override
    public void clearCustomEntityModels() {
        modelRequests.clear();
        super.clearCustomEntityModels();
    }

    @Override
    public void removeCustomEntity(UUID uuid) {
        modelRequests.forget(uuid);
        super.removeCustomEntity(uuid);
    }

    @Override
    public void handle(AnimatePayload payload) {
        // Keep commands behind their model snapshot; they must not overtake deferred construction.
        if (modelRequests.contains(payload.getEntityUuid())) {
            if (payload.getAnimationName() != null && payload.getAnimationName().length() <= 1024) {
                modelRequests.animation(payload.getEntityUuid(), payload.getAnimationName());
            } else {
                invalidModelRequests++;
            }
            return;
        }

        if (deferPlayerAnimation(payload.getEntityUuid(), 2L * payload.getAnimationName().length(), () -> handle(payload))) return;
        if (this.packManager == null) return;
        // 旧协议没有控制器参数，使用默认运行时控制器及动画结束条件。
        final var renderer = this.cachedPlayerRenderers.get(payload.getEntityUuid());
        if (renderer instanceof CustomPlayerRenderer customPlayerRenderer) {
            playPlayerAnimation(customPlayerRenderer, payload.getEntityUuid(), payload.getAnimationName(), "default",
                    "query.any_animation_finished", "__runtime_controller", 0.0F);
            return;
        }

        ViaBedrockUtilityNeoForge.LOGGER.debug("[Entity] ANIMATE: no custom-entity/player renderer for uuid={} (animation '{}')", payload.getEntityUuid(), payload.getAnimationName());
    }

    public void beforeModelFrame(RenderFrameEvent.Pre event) {
        drainModelRequests();
    }

    void drainModelRequests() {
        if (packManager == null) return;
        var profiler = net.minecraft.util.profiling.Profiler.get();
        profiler.push("ec:viabedrockutility/model/pump");
        try {
            modelRequests.drain(MAX_OPERATIONS_PER_FRAME, FRAME_BUDGET_NANOS, System::nanoTime,
                    this::applyModelRequest, this::playCustomAnimation,
                    (id, error) -> lastModelFailure = error.getClass().getSimpleName());
        } finally { profiler.pop(); }
    }

    boolean applyModelRequest(UUID id, ModelRequestState state) {
        final EntityDefinitions.EntityDefinition definition = packManager.getEntityDefinitions().getEntities().get(state.identifier());
        if (definition == null) { unknownModelRequests++; return false; }
        var profiler = net.minecraft.util.profiling.Profiler.get();
        profiler.push("ec:viabedrockutility/model/apply");
        try {
            CustomEntityTicker previous = cachedCustomEntities.get(id);
            CustomEntityTicker ticker = previous;
            if (ticker == null || !ticker.getEntityDefinition().identifier().equals(state.identifier())) {
                ticker = createTicker(packManager, definition);
            }
            state.apply(ticker);
            if (ticker != previous) {
                if (previous != null) previous.getRenderer().invalidateFrozenMeshes("entity_definition_changed");
                cachedCustomEntities.put(id, ticker);
            }
            return true;
        } finally { profiler.pop(); }
    }

    CustomEntityTicker createTicker(PackManager manager, EntityDefinitions.EntityDefinition definition) {
        return new CustomEntityTicker(manager, definition);
    }

    void playCustomAnimation(UUID id, String animation) {
        CustomEntityTicker ticker = cachedCustomEntities.get(id);
        if (ticker == null || packManager == null) return;
        var data = packManager.getAnimationDefinitions().getAnimations().get(animation);
        if (data != null) ticker.getRenderer().playExplicit(data);
    }

    @Override
    public void markCustomEntityHurt(UUID uuid, long currentTick) {
        if (modelRequests.contains(uuid)) getCustomEntityHurtTracker().markHurt(uuid, currentTick);
        else super.markCustomEntityHurt(uuid, currentTick);
    }

    @Override
    public void markCustomEntityDead(UUID uuid) {
        if (modelRequests.contains(uuid)) getCustomEntityHurtTracker().markDead(uuid);
        else super.markCustomEntityDead(uuid);
    }

    public Map<String, Object> modelRequestSnapshot() {
        Map<String, Object> result = new LinkedHashMap<>(modelRequests.snapshot());
        result.put("cachedModels", cachedCustomEntities.size());
        result.put("invalidRequests", invalidModelRequests);
        result.put("unknownModels", unknownModelRequests);
        result.put("lastFailure", lastModelFailure);
        result.put("maxOperationsPerFrame", MAX_OPERATIONS_PER_FRAME);
        result.put("softFrameBudgetNanos", FRAME_BUDGET_NANOS);
        return result;
    }

    @Override
    public void handle(PlayerAnimatePayload payload) {
        long bytes = 2L * (payload.getAnimation().length() + payload.getNextState().length()
                + payload.getStopExpression().length() + payload.getController().length());
        if (deferPlayerAnimation(payload.getPlayerUuid(), bytes, () -> handle(payload))) return;
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

    record ModelRequestState(String identifier, Set<ActorFlags> flags, Integer variant,
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
