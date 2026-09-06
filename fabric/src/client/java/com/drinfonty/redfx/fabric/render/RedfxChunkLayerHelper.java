package com.drinfonty.redfx.fabric.render;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

final class RedfxChunkLayerHelper {
	static void apply(QuadEmitter emitter) {
		emitter.renderLayer(ChunkSectionLayer.CUTOUT);
	}
}
