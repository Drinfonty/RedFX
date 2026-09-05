package com.drinfonty.redfx.canvas;

/**
 * Orientation and coordinate mapping for block faces.
 *
 * Faces are identified by their vanilla 3D data value (0..5).
 * u increases to the right and v increases downwards as seen by a player looking at the face;
 * for UP and DOWN, v increases southwards.
 */
public final class FaceAxes {
	public static final int DOWN = 0;
	public static final int UP = 1;
	public static final int NORTH = 2;
	public static final int SOUTH = 3;
	public static final int WEST = 4;
	public static final int EAST = 5;

	public static final int FACE_COUNT = 6;

	private FaceAxes() {
	}

	public static boolean isValidFace(int face) {
		return face >= 0 && face < FACE_COUNT;
	}

	public static double u(int face, double lx, double ly, double lz) {
		return switch (face) {
			case NORTH -> 1.0 - lx;
			case SOUTH -> lx;
			case WEST -> lz;
			case EAST -> 1.0 - lz;
			case UP, DOWN -> lx;
			default -> throw new IllegalArgumentException("bad face: " + face);
		};
	}

	public static double v(int face, double lx, double ly, double lz) {
		return switch (face) {
			case NORTH, SOUTH, WEST, EAST -> 1.0 - ly;
			case UP -> lz;
			case DOWN -> 1.0 - lz;
			default -> throw new IllegalArgumentException("bad face: " + face);
		};
	}

	public static int texel(double coordinate) {
		int value = (int) Math.floor(coordinate * Canvas.SIZE);
		if (value < 0) {
			return 0;
		}
		return Math.min(value, Canvas.SIZE - 1);
	}
}
