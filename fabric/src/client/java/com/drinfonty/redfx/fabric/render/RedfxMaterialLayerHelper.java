package com.drinfonty.redfx.fabric.render;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

import com.drinfonty.redfx.RedfxMod;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;

final class RedfxMaterialLayerHelper {
	private static Object cachedCutoutMaterial;
	private static Object cachedTranslucentMaterial;
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
			@SuppressWarnings("unchecked")
			Class<? extends Enum> blendModeClass = (Class<? extends Enum>) Class.forName("net.fabricmc.fabric.api.renderer.v1.material.BlendMode");
			Method blendModeMethod = materialFinderMethod.getReturnType().getMethod("blendMode", blendModeClass);
			Method findMethod = materialFinderMethod.getReturnType().getMethod("find");

			@SuppressWarnings("unchecked")
			Enum<?> cutoutEnum = Enum.valueOf(blendModeClass, "CUTOUT");
			Object cutoutFinder = materialFinderMethod.invoke(renderer);
			blendModeMethod.invoke(cutoutFinder, cutoutEnum);
			cachedCutoutMaterial = findMethod.invoke(cutoutFinder);

			@SuppressWarnings("unchecked")
			Enum<?> translucentEnum = Enum.valueOf(blendModeClass, "TRANSLUCENT");
			Object translucentFinder = materialFinderMethod.invoke(renderer);
			blendModeMethod.invoke(translucentFinder, translucentEnum);
			cachedTranslucentMaterial = findMethod.invoke(translucentFinder);

			Class<?> renderMaterialClass = Class.forName("net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial");
			MethodType type = MethodType.methodType(QuadEmitter.class, renderMaterialClass);
			materialMethodHandle = MethodHandles.publicLookup().findVirtual(QuadEmitter.class, "material", type);
		} catch (Throwable t) {
			RedfxMod.LOGGER.error("Failed to initialize legacy FRAPI materials for fallback", t);
		}
	}

	static void apply(QuadEmitter emitter, boolean isTranslucent) {
		if (cachedCutoutMaterial == null || cachedTranslucentMaterial == null) {
			initMaterial();
		}
		Object material = isTranslucent ? cachedTranslucentMaterial : cachedCutoutMaterial;
		if (materialMethodHandle != null && material != null) {
			try {
				materialMethodHandle.invoke(emitter, material);
			} catch (Throwable ignored) {
			}
		}
	}
}
