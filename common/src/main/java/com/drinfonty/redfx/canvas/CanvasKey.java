package com.drinfonty.redfx.canvas;

/**
 * Packs a canvas address into a single long:
 *   bits 0..2: face (0..5)
 *   bits 3..6: local Z (0..15)
 *   bits 7..10: local X (0..15)
 *   bits 11..42: world Y (full signed int)
 */
public final class CanvasKey {
	private static final int FACE_BITS = 3;
	private static final int LOCAL_BITS = 4;

	private static final int Z_SHIFT = FACE_BITS;
	private static final int X_SHIFT = Z_SHIFT + LOCAL_BITS;
	private static final int Y_SHIFT = X_SHIFT + LOCAL_BITS;

	private static final long FACE_MASK = (1L << FACE_BITS) - 1L;
	private static final long LOCAL_MASK = (1L << LOCAL_BITS) - 1L;

	private CanvasKey() {
	}

	public static long pack(int localX, int y, int localZ, int face) {
		if ((localX & ~0xF) != 0 || (localZ & ~0xF) != 0) {
			throw new IllegalArgumentException("chunk-local coordinates out of range: " + localX + "," + localZ);
		}

		if (!FaceAxes.isValidFace(face)) {
			throw new IllegalArgumentException("bad face: " + face);
		}

		return ((long) y << Y_SHIFT)
			| ((long) localX << X_SHIFT)
			| ((long) localZ << Z_SHIFT)
			| face;
	}

	public static int localX(long key) {
		return (int) ((key >> X_SHIFT) & LOCAL_MASK);
	}

	public static int localZ(long key) {
		return (int) ((key >> Z_SHIFT) & LOCAL_MASK);
	}

	public static int y(long key) {
		return (int) (key >> Y_SHIFT);
	}

	public static int face(long key) {
		return (int) (key & FACE_MASK);
	}
}
