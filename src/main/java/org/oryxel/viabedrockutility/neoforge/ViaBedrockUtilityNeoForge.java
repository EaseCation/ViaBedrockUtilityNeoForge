package org.oryxel.viabedrockutility.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.config.LodConfig;
import org.oryxel.viabedrockutility.renderer.DeferredNameTag;
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
		NeoForge.EVENT_BUS.addListener(ViaBedrockUtility.getInstance()::onClientTick);
		NeoForge.EVENT_BUS.addListener(ViaBedrockUtility.getInstance()::registerClientCommands);
		// Replay VBU name tags after all entity geometry is drawn so they show through entities
		// (faint) instead of being painted over. See DeferredNameTag for the rationale.
		NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterEntities event) -> DeferredNameTag.flush());
	}
}
