package com.drinfonty.redfx.canvas;

/**
 * Coordinate transformations and drip calculations for blood splatters that
 * flow and drip over block edges onto adjacent vertical surfaces.
 */
public final class EdgeDrip {
	private EdgeDrip() {
	}

	/**
	 * Computes the horizontal coordinate {@code u_side} on a vertical face
	 * corresponding to top-face coordinates {@code (u, v)}.
	 */
	public static int sideU(int sideFace, int u, int v) {
		int val = switch (sideFace) {
			case FaceAxes.NORTH -> (Canvas.SIZE - 1) - u;
			case FaceAxes.SOUTH -> u;
			case FaceAxes.WEST -> v;
			case FaceAxes.EAST -> (Canvas.SIZE - 1) - v;
			default -> throw new IllegalArgumentException("invalid side face: " + sideFace);
		};
		return Math.max(0, Math.min(Canvas.SIZE - 1, val));
	}

	/**
	 * Computes the distance (overhang) into an air column past a block's edge.
	 * {@code edgeFace} is the direction from the solid block to the air column (NORTH, SOUTH, WEST, EAST).
	 * {@code uTexel} and {@code vTexel} are the coordinates within the air column.
	 */
	public static int overhangDistance(int edgeFace, int uTexel, int vTexel) {
		return switch (edgeFace) {
			case FaceAxes.EAST -> uTexel;
			case FaceAxes.WEST -> (Canvas.SIZE - 1) - uTexel;
			case FaceAxes.SOUTH -> vTexel;
			case FaceAxes.NORTH -> (Canvas.SIZE - 1) - vTexel;
			default -> throw new IllegalArgumentException("invalid edge face: " + edgeFace);
		};
	}

	/**
	 * Computes the organic extra drip length (1..3) down a vertical face for column {@code uSide}.
	 */
	public static int extraDripLength(int blockX, int blockZ, int sideFace, int uSide, int splatIndex) {
		int hash = Math.abs((blockX * 3127 + blockZ * 739 + sideFace * 101 + uSide * 37) ^ (splatIndex * 19));
		return 1 + (hash % 3);
	}

	/**
	 * Computes the attenuated alpha for a drip step down the face.
	 * step 1: ~70% of base
	 * step 2: ~45% of base
	 * step 3: ~25% of base
	 */
	public static int dripAlpha(int baseAlpha, int step) {
		float factor = switch (step) {
			case 1 -> 0.70f;
			case 2 -> 0.45f;
			default -> 0.25f;
		};
		return Math.max(60, (int) (baseAlpha * factor));
	}
}
