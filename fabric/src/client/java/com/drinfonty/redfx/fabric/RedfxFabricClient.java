package com.drinfonty.redfx.fabric;

import com.drinfonty.redfx.RedfxMod;
import com.drinfonty.redfx.client.ClientCanvasStore;
import com.drinfonty.redfx.client.render.PaintSprites;
import com.drinfonty.redfx.fabric.render.RedfxWrapperModel;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class RedfxFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		RedfxMod.LOGGER.info("Initializing RedfxMod client entry point!");

		ModelLoadingPlugin.register(context -> {
			PaintSprites.invalidate();
			context.modifyBlockModelAfterBake().register(ModelModifier.WRAP_LAST_PHASE,
				(model, modifierContext) -> new RedfxWrapperModel(model));
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level != null) {
				ClientCanvasStore.get().tickExpiration(System.currentTimeMillis());
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientCanvasStore.get().clearAll();
			PaintSprites.invalidate();
		});

		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
			var pos = chunk.getPos();
			ClientCanvasStore.get().clearChunk(pos.x(), pos.z(), false);
		});
	}
}
