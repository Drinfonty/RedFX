package com.drinfonty.redfx.fabric.render;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

import com.drinfonty.redfx.RedfxMod;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;

final class RedfxMaterialLayerHelper {
	private static Object cachedMaterial;
	private static MethodHandle materialMethodHandle;
	private static boolean initialized = false;

	private RedfxMaterialLayerHelper() {
	}

	private static synchronized void initMaterial() {
		if (initialized) {
			return;
		}
		initialized = true;

		try {
			Renderer renderer = Renderer.get();
			if (renderer == null) {
				initialized = false;
				return;
			}

			Method materialFinderMethod = renderer.getClass().getMethod("materialFinder");
			Object finder = materialFinderMethod.invoke(renderer);

			@SuppressWarnings("unchecked")
			Class<? extends Enum> blendModeClass = (Class<? extends Enum>) Class.forName("net.fabricmc.fabric.api.renderer.v1.material.BlendMode");
			@SuppressWarnings("unchecked")
			Enum<?> cutoutEnum = Enum.valueOf(blendModeClass, "CUTOUT");

			Method blendModeMethod = finder.getClass().getMethod("blendMode", blendModeClass);
			blendModeMethod.invoke(finder, cutoutEnum);

			Method findMethod = finder.getClass().getMethod("find");
			cachedMaterial = findMethod.invoke(finder);

			Class<?> renderMaterialClass = Class.forName("net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial");
			MethodType type = MethodType.methodType(QuadEmitter.class, renderMaterialClass);
			materialMethodHandle = MethodHandles.publicLookup().findVirtual(QuadEmitter.class, "material", type);
		} catch (Throwable t) {
			RedfxMod.LOGGER.error("Failed to initialize legacy FRAPI cutout material for 1.21.5 fallback", t);
		}
	}

	static void apply(QuadEmitter emitter) {
		if (cachedMaterial == null) {
			initMaterial();
		}
		if (materialMethodHandle != null && cachedMaterial != null) {
			try {
				materialMethodHandle.invoke(emitter, cachedMaterial);
			} catch (Throwable ignored) {
			}
		}
	}
}
