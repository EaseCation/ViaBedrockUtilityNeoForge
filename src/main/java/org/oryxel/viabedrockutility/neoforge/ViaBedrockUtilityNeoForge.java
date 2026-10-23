package org.oryxel.viabedrockutility.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.config.LodConfig;
import org.oryxel.viabedrockutility.renderer.AnimationBudget;
import org.oryxel.viabedrockutility.renderer.DeferredNameTag;
import org.oryxel.viabedrockutility.renderer.FrozenEntityMeshCache;
import org.oryxel.viabedrockutility.renderer.FrozenMeshDrawQueue;
import org.oryxel.viabedrockutility.renderer.PoseMeshRebuildBudget;
import org.oryxel.viabedrockutility.renderer.PlayerTranslucencyRenderer;
import org.oryxel.viabedrockutility.renderer.VbuRenderMetrics;
import org.oryxel.viabedrockutility.attachable.FirstPersonAttachableRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = ViaBedrockUtilityNeoForge.MOD_ID, dist = Dist.CLIENT)
public class ViaBedrockUtilityNeoForge {
	public static final String MOD_ID = "viabedrockutility";
	public static final Logger LOGGER = net.easecation.logging.AsyncLogger.wrap(LoggerFactory.getLogger(MOD_ID));

	public ViaBedrockUtilityNeoForge(IEventBus modEventBus) {
        net.easecation.bedrockmotion.render.RenderControllerEvaluator.setDiagnosticLogger(
                net.easecation.logging.AsyncLogger.wrap(LoggerFactory.getLogger(
                        net.easecation.bedrockmotion.render.RenderControllerEvaluator.class)));
        net.easecation.bedrockmotion.controller.AnimationControllerInstance.setDiagnosticLogger(
                net.easecation.logging.AsyncLogger.wrap(LoggerFactory.getLogger(
                        net.easecation.bedrockmotion.controller.AnimationControllerInstance.class)));
		LOGGER.debug("ViaBedrockUtility initialized");
		modEventBus.addListener((RegisterRenderPipelinesEvent event) -> event.registerPipeline(PlayerTranslucencyRenderer.COMPOSITE));
		NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> PlayerTranslucencyRenderer.close());
		LodConfig.load();
		ViaBedrockUtility.getInstance().init();
		modEventBus.addListener(ViaBedrockUtility.getInstance()::registerPayloads);
		// Retire client objects that retain resources or mutable animation state across reload generations.
		modEventBus.addListener((AddClientReloadListenersEvent event) -> event.addListener(
				ResourceLocation.fromNamespaceAndPath(MOD_ID, "runtime_caches"),
				(ResourceManagerReloadListener) resourceManager -> {
					DeferredNameTag.clearCache();
					PlayerTranslucencyRenderer.close();
					FrozenMeshDrawQueue.clear();
					FrozenEntityMeshCache.global().invalidateAll("resource_reload");
					final var payloadHandler = ViaBedrockUtility.getInstance().getPayloadHandler();
					if (payloadHandler != null) {
						payloadHandler.resetPlayerAnimationRuntimes();
					}
				}));
		NeoForge.EVENT_BUS.addListener(ViaBedrockUtility.getInstance()::onClientTick);
		NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
				ViaBedrockUtility.getInstance().getPayloadHandler()::onMovementInputUpdate);
		NeoForge.EVENT_BUS.addListener(ViaBedrockUtility.getInstance()::registerClientCommands);
		NeoForge.EVENT_BUS.addListener(FirstPersonAttachableRenderer::onRenderHand);
		// Reset the per-frame animation budget before entities render (AfterOpaqueBlocks fires before
		// the entity pass). Caps full-rate MoLang animation count in dense scenes. See AnimationBudget.
		NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterOpaqueBlocks event) -> {
			AnimationBudget.reset();
			PoseMeshRebuildBudget.reset();
			FrozenMeshDrawQueue.clear();
			// Only name tags submitted after the main opaque pass belong to the main camera. Iris has
			// already rendered shadow-map entities with a different projection and no AfterEntities event.
			DeferredNameTag.beginMainEntityPass();
			PlayerTranslucencyRenderer.beginFrame();
		});
		NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterEntities event) -> {
			FrozenMeshDrawQueue.flush();
			PlayerTranslucencyRenderer.endEntityPass();
		});
		// Complete opaque block entities before ghost composition, then replay unchanged name tags.
		NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterBlockEntities event) -> {
			try {
				PlayerTranslucencyRenderer.flush();
				DeferredNameTag.flush();
			} finally {
				// Prevent an exception in either deferred renderer from leaving the next auxiliary pass armed.
				DeferredNameTag.endMainEntityPass();
				VbuRenderMetrics.endFrame();
				PoseMeshRebuildBudget.endFrame();
			}
		});
		// Keep cached player Bedrock skins/models across ordinary RemoveEntity/AddPlayer cycles. Nukkit
		// Human NPCs may only send PlayerList skin data once, so final cleanup belongs to disconnect.
	}
}
