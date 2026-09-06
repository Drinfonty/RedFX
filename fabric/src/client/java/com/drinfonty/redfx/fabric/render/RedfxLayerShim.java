package com.drinfonty.redfx.fabric.render;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;

public final class RedfxLayerShim {
	private static final boolean HAS_CHUNK_LAYER;

	static {
		boolean ok = false;
		try {
			Class.forName("com.drinfonty.redfx.fabric.render.RedfxChunkLayerHelper", true, RedfxLayerShim.class.getClassLoader());
			ok = true;
		} catch (Throwable ignored) {
			ok = false;
		}
		HAS_CHUNK_LAYER = ok;
	}

	private RedfxLayerShim() {
	}

	public static void applyCutout(QuadEmitter emitter) {
		if (HAS_CHUNK_LAYER) {
			RedfxChunkLayerHelper.apply(emitter);
		} else {
			RedfxMaterialLayerHelper.apply(emitter);
		}
	}
}
