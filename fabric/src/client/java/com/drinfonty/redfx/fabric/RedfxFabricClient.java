package com.drinfonty.redfx.fabric;

import com.drinfonty.redfx.RedfxMod;
import net.fabricmc.api.ClientModInitializer;

public class RedfxFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		RedfxMod.LOGGER.info("Initializing RedfxMod client entry point!");
	}
}
