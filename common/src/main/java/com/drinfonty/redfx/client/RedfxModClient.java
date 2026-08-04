package com.drinfonty.redfx.client;

import com.drinfonty.redfx.RedfxMod;
import net.fabricmc.api.ClientModInitializer;

public class RedfxModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		RedfxMod.LOGGER.info("Initializing RedfxMod client entry point!");
	}
}
