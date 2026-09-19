package com.drinfonty.redfx.fabric.render;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;

public final class RedfxLayerShim {
	private RedfxLayerShim() {
	}

	public static void apply(QuadEmitter emitter, boolean isTranslucent) {
		if (RedfxChunkLayerHelper.isAvailable()) {
			RedfxChunkLayerHelper.apply(emitter, isTranslucent);
		} else {
			RedfxMaterialLayerHelper.apply(emitter, isTranslucent);
		}
	}
}
