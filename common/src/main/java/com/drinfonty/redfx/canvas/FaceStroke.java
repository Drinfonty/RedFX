package com.drinfonty.redfx.canvas;

/**
 * Coordinate transformations across block faces on the same 2D plane.
 *
 * Maps face-plane block coordinates and local coords into global units
 * where each block spans 16 texels (UNITS_PER_BLOCK = 16).
 */
public final class FaceStroke {
	public static final int UNITS_PER_BLOCK = Canvas.SIZE; // 16 texels per block

	private FaceStroke() {
	}

	public static boolean uInverted(int face) {
		return face == FaceAxes.NORTH || face == FaceAxes.EAST;
	}

	public static boolean vInverted(int face) {
		return face != FaceAxes.UP;
	}

	public static int uWorldAxis(int face) {
		return switch (face) {
			case FaceAxes.UP, FaceAxes.DOWN, FaceAxes.NORTH, FaceAxes.SOUTH -> 0; // X
			case FaceAxes.WEST, FaceAxes.EAST -> 2; // Z
			default -> throw new IllegalArgumentException("bad face: " + face);
		};
	}

	public static int vWorldAxis(int face) {
		return switch (face) {
			case FaceAxes.UP, FaceAxes.DOWN -> 2; // Z
			case FaceAxes.NORTH, FaceAxes.SOUTH, FaceAxes.WEST, FaceAxes.EAST -> 1; // Y
			default -> throw new IllegalArgumentException("bad face: " + face);
		};
	}

	public static int normalWorldAxis(int face) {
		return switch (face) {
			case FaceAxes.UP, FaceAxes.DOWN -> 1; // Y
			case FaceAxes.NORTH, FaceAxes.SOUTH -> 2; // Z
			case FaceAxes.WEST, FaceAxes.EAST -> 0; // X
			default -> throw new IllegalArgumentException("bad face: " + face);
		};
	}

	public static int encodeU(int face, int blockCoord, int uTexel) {
		return uInverted(face)
			? -blockCoord * UNITS_PER_BLOCK - UNITS_PER_BLOCK + uTexel
			: blockCoord * UNITS_PER_BLOCK + uTexel;
	}

	public static int encodeV(int face, int blockCoord, int vTexel) {
		return vInverted(face)
			? -blockCoord * UNITS_PER_BLOCK - UNITS_PER_BLOCK + vTexel
			: blockCoord * UNITS_PER_BLOCK + vTexel;
	}

	public static int blockOfU(int face, int globalU) {
		return uInverted(face)
			? Math.floorDiv(-globalU - 1, UNITS_PER_BLOCK)
			: Math.floorDiv(globalU, UNITS_PER_BLOCK);
	}

	public static int blockOfV(int face, int globalV) {
		return vInverted(face)
			? Math.floorDiv(-globalV - 1, UNITS_PER_BLOCK)
			: Math.floorDiv(globalV, UNITS_PER_BLOCK);
	}

	private static int axis(int which, int x, int y, int z) {
		return switch (which) {
			case 0 -> x;
			case 1 -> y;
			default -> z;
		};
	}

	public static int blockU(int face, int x, int y, int z) {
		return axis(uWorldAxis(face), x, y, z);
	}

	public static int blockV(int face, int x, int y, int z) {
		return axis(vWorldAxis(face), x, y, z);
	}

	public static int normal(int face, int x, int y, int z) {
		return axis(normalWorldAxis(face), x, y, z);
	}

	public static int worldX(int face, int blockU, int blockV, int normal) {
		return rebuild(face, 0, blockU, blockV, normal);
	}

	public static int worldY(int face, int blockU, int blockV, int normal) {
		return rebuild(face, 1, blockU, blockV, normal);
	}

	public static int worldZ(int face, int blockU, int blockV, int normal) {
		return rebuild(face, 2, blockU, blockV, normal);
	}

	private static int rebuild(int face, int which, int blockU, int blockV, int normal) {
		if (which == uWorldAxis(face)) {
			return blockU;
		}
		if (which == vWorldAxis(face)) {
			return blockV;
		}
		return normal;
	}
}
