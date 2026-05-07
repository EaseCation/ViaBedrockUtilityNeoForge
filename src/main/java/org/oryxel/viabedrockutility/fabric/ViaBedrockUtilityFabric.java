package org.oryxel.viabedrockutility.fabric;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.config.LodConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ViaBedrockUtilityFabric.MOD_ID)
public class ViaBedrockUtilityFabric {
	public static final String MOD_ID = "viabedrockutility";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public ViaBedrockUtilityFabric(IEventBus modEventBus) {
		LOGGER.debug("ViaBedrockUtility initialized");
		LodConfig.load();
		ViaBedrockUtility.getInstance().init();
		modEventBus.addListener(ViaBedrockUtility.getInstance()::registerPayloads);
		NeoForge.EVENT_BUS.addListener(ViaBedrockUtility.getInstance()::onClientTick);
		NeoForge.EVENT_BUS.addListener(ViaBedrockUtility.getInstance()::registerClientCommands);
	}
}
