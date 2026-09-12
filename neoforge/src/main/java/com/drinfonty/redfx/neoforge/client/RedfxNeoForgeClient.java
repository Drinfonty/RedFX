package com.drinfonty.redfx.neoforge.client;

import com.drinfonty.redfx.RedfxMod;
import com.drinfonty.redfx.client.ClientCanvasStore;
import com.drinfonty.redfx.client.render.PaintSprites;
import com.drinfonty.redfx.neoforge.client.render.RedfxDynamicModel;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class RedfxNeoForgeClient {
	private RedfxNeoForgeClient() {
	}

	public static void init(IEventBus modBus) {
		modBus.addListener(ModelEvent.ModifyBakingResult.class, event -> {
			event.getBakingResult().blockStateModels().replaceAll((loc, model) -> new RedfxDynamicModel(model));
			RedfxDynamicModel.clearCache();
			PaintSprites.invalidate();
		});

		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
			ClientCanvasStore.get().tickExpiration(System.currentTimeMillis());
		});

		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
			ClientCanvasStore.get().clearAll();
			RedfxDynamicModel.clearCache();
			PaintSprites.invalidate();
		});

		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.level.ChunkEvent.Unload.class, event -> {
			if (event.getLevel() != null && event.getLevel().isClientSide() && event.getChunk() != null) {
				var pos = event.getChunk().getPos();
				ClientCanvasStore.get().clearChunk(pos.x, pos.z, false);
			}
		});
	}
}
