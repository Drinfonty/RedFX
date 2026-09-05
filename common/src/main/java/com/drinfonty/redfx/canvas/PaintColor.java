package com.drinfonty.redfx.canvas;

/**
 * ARGB texel helpers.
 */
public final class PaintColor {
	public static final int EMPTY = 0;

	private PaintColor() {
	}

	public static boolean isPainted(int texel) {
		return (texel >>> 24) != 0;
	}

	public static int opaque(int rgb) {
		return 0xFF000000 | (rgb & 0xFFFFFF);
	}

	public static int normalise(int texel) {
		return isPainted(texel) ? opaque(texel) : EMPTY;
	}

	public static int fromRgb(float r, float g, float b) {
		int ir = Math.max(0, Math.min(255, (int) (r * 255.0f)));
		int ig = Math.max(0, Math.min(255, (int) (g * 255.0f)));
		int ib = Math.max(0, Math.min(255, (int) (b * 255.0f)));
		return 0xFF000000 | (ir << 16) | (ig << 8) | ib;
	}
}
