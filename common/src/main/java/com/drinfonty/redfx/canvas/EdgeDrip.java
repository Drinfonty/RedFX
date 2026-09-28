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
	 * Selects 1 or at most 2 separated column coordinates within [clusterStart, clusterEnd]
	 * to trickle single-texel teardrop beads down the side face.
	 * Rivulets are eliminated by ensuring selected bead columns are never adjacent.
	 */
	public static int[] selectBeadColumns(int clusterStart, int clusterEnd, int blockX, int blockZ, int sideFace, int splatIndex) {
		int clusterWidth = clusterEnd - clusterStart + 1;
		if (clusterWidth <= 0) return new int[0];
		if (clusterWidth == 1) return new int[] { clusterStart };

		int hash = Math.abs((blockX * 3127 + blockZ * 739 + sideFace * 101 + clusterStart * 37) ^ (splatIndex * 19));

		// For narrow to medium clusters (width <= 5), choose exactly 1 bead column at random
		if (clusterWidth <= 5) {
			int bead = clusterStart + (hash % clusterWidth);
			return new int[] { bead };
		}

		// For wider clusters, pick 1 or at most 2 separated bead columns
		int b1 = clusterStart + (hash % (clusterWidth / 2));
		int b2 = clusterEnd - ((hash / 17) % (clusterWidth / 2));

		if (b2 - b1 >= 2) {
			return new int[] { b1, b2 };
		} else {
			return new int[] { b1 };
		}
	}

	/**
	 * Computes the teardrop bead drip length down a vertical face for column {@code uSide}.
	 * Provides high variety across short trickles, medium beads, long drips, up to the full block height.
	 */
	public static int calculateDripLength(int blockX, int blockZ, int sideFace, int uSide,
		int clusterStart, int clusterEnd, int maxAllowedLength, int splatIndex) {
		if (maxAllowedLength <= 1) {
			return Math.max(0, maxAllowedLength);
		}

		int hash = Math.abs((blockX * 3127 + blockZ * 739 + sideFace * 101 + uSide * 37) ^ (splatIndex * 19));
		int category = hash % 100;

		int len;
		if (category < 25) {
			// ~25% reach the base: full allowed length
			len = maxAllowedLength;
		} else if (category < 50) {
			// ~25% short drips: 2..max(2, round(0.35 * maxAllowedLength))
			int maxShort = Math.max(2, Math.min(maxAllowedLength, Math.round(maxAllowedLength * 0.35f)));
			int span = Math.max(1, maxShort - 2 + 1);
			len = 2 + ((hash / 100) % span);
		} else if (category < 80) {
			// ~30% medium drips: ~35% to ~70% of maxAllowedLength
			int minMed = Math.max(2, Math.round(maxAllowedLength * 0.35f));
			int maxMed = Math.max(minMed, Math.min(maxAllowedLength, Math.round(maxAllowedLength * 0.70f)));
			int span = Math.max(1, maxMed - minMed + 1);
			len = minMed + ((hash / 100) % span);
		} else {
			// ~20% long drips: ~70% to maxAllowedLength
			int minLong = Math.max(2, Math.round(maxAllowedLength * 0.70f));
			int span = Math.max(1, maxAllowedLength - minLong + 1);
			len = minLong + ((hash / 100) % span);
		}

		return Math.max(1, Math.min(maxAllowedLength, len));
	}

	/**
	 * Computes the step delay in milliseconds for a bead of length {@code beadLen} working backward from length.
	 * Total drip duration is paced naturally (~1.4s to 2.2s), resulting in lower step delay (faster pixel rate)
	 * for longer drips and higher step delay (slower pixel rate) for shorter drips.
	 */
	public static long beadStepDelayMs(int beadLen, float configFactor, int hash) {
		int totalDurationMs = 1400 + Math.abs(hash % 800); // 1400ms to 2200ms
		float stepDelay = (float) totalDurationMs / (float) Math.max(1, beadLen);
		float clampedDelay = Math.max(75.0f, Math.min(350.0f, stepDelay));
		return Math.max(40L, Math.round(clampedDelay * configFactor));
	}

	/**
	 * Overload for backward-compatibility with default hash derived from bead length.
	 */
	public static long beadStepDelayMs(int beadLen, float configFactor) {
		return beadStepDelayMs(beadLen, configFactor, beadLen * 31);
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

	/**
	 * Computes the attenuated alpha for step {@code step} of a teardrop bead drip of total length {@code totalLength}.
	 * The top rim stays rich, the stream is slender and slightly translucent, and the tip forms a dense teardrop bead.
	 */
	public static int dripAlpha(int baseAlpha, int step, int totalLength) {
		if (step <= 0) {
			return Math.max(220, baseAlpha);
		}
		if (step >= totalLength) {
			// Teardrop bead at the tip: concentrated, rich droplet
			return Math.min(255, Math.max(245, baseAlpha));
		}
		// Slender stream connecting top edge to bead tip
		return Math.max(160, (int) (Math.max(190, baseAlpha) * 0.80f));
	}
}

