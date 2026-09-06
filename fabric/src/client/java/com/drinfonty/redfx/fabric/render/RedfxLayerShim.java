package com.drinfonty.redfx.fabric.render;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;

public final class RedfxLayerShim {
	private RedfxLayerShim() {
	}

	public static void applyCutout(QuadEmitter emitter) {
		if (RedfxChunkLayerHelper.isAvailable()) {
			RedfxChunkLayerHelper.apply(emitter);
		} else {
			RedfxMaterialLayerHelper.apply(emitter);
		}
	}
}
