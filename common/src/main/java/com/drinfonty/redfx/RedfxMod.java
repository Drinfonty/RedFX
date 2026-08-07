package com.drinfonty.redfx;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedfxMod {
	public static final String MOD_ID = "redfx";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * Per-hit diagnostics, off unless {@code -Dredfx.debug=true} is passed. The Gradle dev
	 * run configurations set it; a real launcher never does, so players never see it.
	 *
	 * <p>Guard at the call site rather than logging unconditionally - these fire on every
	 * hit and their arguments are not free to compute. Being {@code static final}, the JIT
	 * folds the branch away entirely when disabled.
	 */
	public static final boolean DEBUG = Boolean.getBoolean("redfx.debug");

	public static void init() {
		LOGGER.info("Initializing RedfxMod common entry point!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
