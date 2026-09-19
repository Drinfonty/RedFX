package com.drinfonty.redfx.fabric.render;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.loader.api.FabricLoader;

final class RedfxChunkLayerHelper {
	private static Object cutoutLayer;
	private static Object translucentLayer;
	private static MethodHandle renderLayerHandle;
	private static boolean available = false;
	private static boolean initialized = false;

	private RedfxChunkLayerHelper() {
	}

	static synchronized boolean isAvailable() {
		if (initialized) {
			return available;
		}
		initialized = true;

		try {
			String layerClassName = FabricLoader.getInstance().getMappingResolver()
				.mapClassName("intermediary", "net.minecraft.class_11515");
			Class<?> layerClass = Class.forName(layerClassName);

			@SuppressWarnings("unchecked")
			Class<? extends Enum> enumClass = (Class<? extends Enum>) layerClass;
			cutoutLayer = Enum.valueOf(enumClass, "CUTOUT");
			translucentLayer = Enum.valueOf(enumClass, "TRANSLUCENT");

			MethodType type = MethodType.methodType(QuadEmitter.class, layerClass);
			renderLayerHandle = MethodHandles.publicLookup().findVirtual(QuadEmitter.class, "renderLayer", type);
			available = (cutoutLayer != null && renderLayerHandle != null);
		} catch (Throwable t) {
			available = false;
		}
		return available;
	}

	static void apply(QuadEmitter emitter, boolean isTranslucent) {
		if (isAvailable() && renderLayerHandle != null) {
			Object layer = isTranslucent ? translucentLayer : cutoutLayer;
			if (layer != null) {
				try {
					renderLayerHandle.invoke(emitter, layer);
				} catch (Throwable ignored) {
				}
			}
		}
	}
}
