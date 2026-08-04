package com.drinfonty.redfx.fabric;

import com.drinfonty.redfx.RedfxMod;
import net.fabricmc.api.ModInitializer;

public class RedfxFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		RedfxMod.init();
	}
}
