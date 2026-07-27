package org.oryxel.viabedrockutility.payload.handler;

import lombok.Getter;
import org.oryxel.viabedrockutility.entity.CustomEntityTicker;
import org.oryxel.viabedrockutility.neoforge.ViaBedrockUtilityNeoForge;
import org.oryxel.viabedrockutility.material.VanillaMaterials;
import org.oryxel.viabedrockutility.material.data.Material;
import net.easecation.bedrockmotion.pack.definitions.EntityDefinitions;
import org.oryxel.viabedrockutility.payload.PayloadHandler;
import org.oryxel.viabedrockutility.payload.impl.entity.AnimatePayload;
import org.oryxel.viabedrockutility.payload.impl.entity.ModelRequestPayload;
import org.oryxel.viabedrockutility.renderer.CustomPlayerRenderer;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.binding.JavaObjectBinding;
import team.unnamed.mocha.runtime.standard.MochaMath;

@Getter
public class CustomEntityPayloadHandler extends PayloadHandler {
    public static final Scope BASE_SCOPE = Scope.create();
    public static final Material DEFAULT_MATERIAL;

    static {
        //noinspection UnstableApiUsage
        BASE_SCOPE.set("math", JavaObjectBinding.of(MochaMath.class, null, new MochaMath()));
        BASE_SCOPE.readOnly(true);

        DEFAULT_MATERIAL = VanillaMaterials.getMaterial("entity");
    }

    @Override
    public void handle(ModelRequestPayload payload) {
        if (!this.packManager.getEntityDefinitions().getEntities().containsKey(payload.getIdentifier())) {
            ViaBedrockUtilityNeoForge.LOGGER.warn("[Entity] MODEL_REQUEST for unknown entity '{}' (uuid={}), skipping", payload.getIdentifier(), payload.getUuid());
            return;
        }

        ViaBedrockUtilityNeoForge.LOGGER.debug("[Entity] MODEL_REQUEST for '{}' (uuid={}) skinId={} variant={} markVariant={} scale={}",
            payload.getIdentifier(), payload.getUuid(),
            payload.getEntityData().skinId(), payload.getEntityData().variant(),
            payload.getEntityData().mark_variant(), payload.getEntityData().scale());
        final EntityDefinitions.EntityDefinition definition = this.packManager.getEntityDefinitions().getEntities().get(payload.getIdentifier());

        CustomEntityTicker ticker = this.cachedCustomEntities.get(payload.getUuid());
        if (ticker == null) {
            ViaBedrockUtilityNeoForge.LOGGER.debug("[Entity] Creating new CustomEntityTicker for '{}' (uuid={})", payload.getIdentifier(), payload.getUuid());
            ticker = new CustomEntityTicker(definition);
            this.cachedCustomEntities.put(payload.getUuid(), ticker);
        }

        ticker.setEntityFlags(payload.getEntityData().flags());
        ticker.updateRenderQueries(
                payload.getEntityData().variant(),
                payload.getEntityData().mark_variant(),
                payload.getEntityData().skinId());
        ticker.setScale(payload.getEntityData().scale());

        ticker.update();
    }

    @Override
    public void handle(AnimatePayload payload) {
        if (this.packManager == null) {
            return;
        }
        final var animData = this.packManager.getAnimationDefinitions().getAnimations().get(payload.getAnimationName());
        if (animData == null) {
            ViaBedrockUtilityNeoForge.LOGGER.debug("[Entity] ANIMATE: animation '{}' not found in PackManager (uuid={})", payload.getAnimationName(), payload.getEntityUuid());
            return;
        }

        // 1) Custom model entity (MODEL_REQUEST path).
        final CustomEntityTicker ticker = this.cachedCustomEntities.get(payload.getEntityUuid());
        if (ticker != null) {
            ticker.getRenderer().playExplicit(animData);
            return;
        }

        // 2) Player / humanoid NPC (player render path): play it as a one-shot on the player's animation manager.
        final var renderer = this.cachedPlayerRenderers.get(payload.getEntityUuid());
        if (renderer instanceof CustomPlayerRenderer customPlayerRenderer) {
            customPlayerRenderer.playAnimationOnce(payload.getAnimationName(), animData);
            return;
        }

        ViaBedrockUtilityNeoForge.LOGGER.debug("[Entity] ANIMATE: no custom-entity/player renderer for uuid={} (animation '{}')", payload.getEntityUuid(), payload.getAnimationName());
    }
}
