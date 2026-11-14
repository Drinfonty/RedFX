package com.drinfonty.redfx.neoforge.client;

import com.drinfonty.redfx.client.ClientCanvasStore;
import com.drinfonty.redfx.client.render.PaintSprites;
import com.drinfonty.redfx.neoforge.client.render.RedfxDynamicModel;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public final class RedfxNeoForgeClient {
	private RedfxNeoForgeClient() {
	}

	public static void init(IEventBus modBus) {
		modBus.addListener((ModelEvent.ModifyBakingResult event) -> {
			event.getModels().replaceAll((loc, model) -> new RedfxDynamicModel(model));
			RedfxDynamicModel.clearCache();
			PaintSprites.invalidate();
		});

		MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
			if (event.phase == TickEvent.Phase.END) {
				ClientCanvasStore.get().tickExpiration(System.currentTimeMillis());
			}
		});

		MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
			ClientCanvasStore.get().clearAll();
			RedfxDynamicModel.clearCache();
			PaintSprites.invalidate();
		});

		MinecraftForge.EVENT_BUS.addListener((ChunkEvent.Unload event) -> {
			if (event.getLevel() != null && event.getLevel().isClientSide() && event.getChunk() != null) {
				var pos = event.getChunk().getPos();
				ClientCanvasStore.get().clearChunk(pos.x, pos.z, false);
			}
		});
	}
}
