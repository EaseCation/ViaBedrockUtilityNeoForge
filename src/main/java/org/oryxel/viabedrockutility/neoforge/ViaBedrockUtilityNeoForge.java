package org.oryxel.viabedrockutility.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.config.LodConfig;
import org.oryxel.viabedrockutility.renderer.AnimationBudget;
import org.oryxel.viabedrockutility.renderer.DeferredNameTag;
import org.oryxel.viabedrockutility.renderer.FrozenEntityMeshCache;
import org.oryxel.viabedrockutility.renderer.FrozenMeshDrawQueue;
import org.oryxel.viabedrockutility.renderer.VbuRenderMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = ViaBedrockUtilityNeoForge.MOD_ID, dist = Dist.CLIENT)
public class ViaBedrockUtilityNeoForge {
	public static final String MOD_ID = "viabedrockutility";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public ViaBedrockUtilityNeoForge(IEventBus modEventBus) {
		LOGGER.debug("ViaBedrockUtility initialized");
		LodConfig.load();
		ViaBedrockUtility.getInstance().init();
		modEventBus.addListener(ViaBedrockUtility.getInstance()::registerPayloads);
		// Clear the deferred name-tag PreparedText cache on client resource reload: cached glyphs hold
		// BakedGlyph references into the font atlas, which the reload rebuilds. See DeferredNameTag.clearCache.
		modEventBus.addListener((AddClientReloadListenersEvent event) -> event.addListener(
				ResourceLocation.fromNamespaceAndPath(MOD_ID, "name_tag_cache"),
				(ResourceManagerReloadListener) resourceManager -> {
					DeferredNameTag.clearCache();
					FrozenMeshDrawQueue.clear();
					FrozenEntityMeshCache.global().invalidateAll("resource_reload");
				}));
		NeoForge.EVENT_BUS.addListener(ViaBedrockUtility.getInstance()::onClientTick);
		NeoForge.EVENT_BUS.addListener(ViaBedrockUtility.getInstance()::registerClientCommands);
		// Reset the per-frame animation budget before entities render (AfterOpaqueBlocks fires before
		// the entity pass). Caps full-rate MoLang animation count in dense scenes. See AnimationBudget.
		NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterOpaqueBlocks event) -> {
			AnimationBudget.reset();
			FrozenMeshDrawQueue.clear();
		});
		// Replay VBU name tags after all entity geometry is drawn so they show through entities
		// (faint) instead of being painted over. See DeferredNameTag for the rationale.
		NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterEntities event) -> {
			try {
				FrozenMeshDrawQueue.flush();
				DeferredNameTag.flush();
			} finally {
				VbuRenderMetrics.endFrame();
			}
		});
		// Keep cached player Bedrock skins/models across ordinary RemoveEntity/AddPlayer cycles. Nukkit
		// Human NPCs may only send PlayerList skin data once, so final cleanup belongs to disconnect.
	}
}
