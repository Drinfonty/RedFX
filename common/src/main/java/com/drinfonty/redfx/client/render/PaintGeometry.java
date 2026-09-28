package com.drinfonty.redfx.client.render;

import com.drinfonty.redfx.canvas.Canvas;
import com.drinfonty.redfx.canvas.FaceAxes;

/**
 * Calculates 3D block-local vertex positions for a PaintQuad on a given face.
 */
public final class PaintGeometry {
	public static final float DECAL_OFFSET = 0.005F;
	private static final float TEXEL = 1.0F / Canvas.SIZE;

	private PaintGeometry() {
	}

	public static float defaultFacePlane(int face, float surfaceY) {
		return switch (face) {
			case FaceAxes.UP -> surfaceY;
			case FaceAxes.DOWN, FaceAxes.NORTH, FaceAxes.WEST -> 0.0F;
			case FaceAxes.SOUTH, FaceAxes.EAST -> 1.0F;
			default -> 0.0F;
		};
	}

	public static float[] corners(PaintQuad quad, float[] out) {
		return corners(quad, out, defaultFacePlane(quad.face(), 1.0F), 1.0F);
	}

	public static float[] corners(PaintQuad quad, float[] out, float surfaceY) {
		return corners(quad, out, defaultFacePlane(quad.face(), surfaceY), surfaceY);
	}

	public static float[] corners(PaintQuad quad, float[] out, float facePlane, float surfaceY) {
		if (out.length < 12) {
			throw new IllegalArgumentException("need room for 12 floats");
		}
		if (Float.isNaN(facePlane)) {
			facePlane = defaultFacePlane(quad.face(), surfaceY);
		}

		float u0 = quad.minU() * TEXEL;
		float u1 = quad.maxU() * TEXEL;
		float v0 = quad.minV() * TEXEL;
		float v1 = quad.maxV() * TEXEL;

		// (u, v) walked anticlockwise as seen from outside the face:
		// bottom-left, bottom-right, top-right, top-left.
		write(out, 0, quad.face(), u0, v1, facePlane, surfaceY);
		write(out, 3, quad.face(), u1, v1, facePlane, surfaceY);
		write(out, 6, quad.face(), u1, v0, facePlane, surfaceY);
		write(out, 9, quad.face(), u0, v0, facePlane, surfaceY);

		return out;
	}

	private static void write(float[] out, int offset, int face, float u, float v, float facePlane, float surfaceY) {
		float x;
		float y;
		float z;
		float d = DECAL_OFFSET;

		switch (face) {
			case FaceAxes.NORTH -> {
				x = (u == 0.0F) ? (1.0F + d) : (u == 1.0F ? -d : (1.0F - u));
				y = (v == 0.0F) ? (surfaceY + d) : (surfaceY - v);
				z = facePlane - d;
			}
			case FaceAxes.SOUTH -> {
				x = (u == 0.0F) ? -d : (u == 1.0F ? 1.0F + d : u);
				y = (v == 0.0F) ? (surfaceY + d) : (surfaceY - v);
				z = facePlane + d;
			}
			case FaceAxes.WEST -> {
				x = facePlane - d;
				y = (v == 0.0F) ? (surfaceY + d) : (surfaceY - v);
				z = (u == 0.0F) ? -d : (u == 1.0F ? 1.0F + d : u);
			}
			case FaceAxes.EAST -> {
				x = facePlane + d;
				y = (v == 0.0F) ? (surfaceY + d) : (surfaceY - v);
				z = (u == 0.0F) ? (1.0F + d) : (u == 1.0F ? -d : (1.0F - u));
			}
			case FaceAxes.UP -> {
				x = (u == 0.0F) ? -d : (u == 1.0F ? 1.0F + d : u);
				y = surfaceY + d;
				z = (v == 0.0F) ? -d : (v == 1.0F ? 1.0F + d : v);
			}
			case FaceAxes.DOWN -> {
				x = (u == 0.0F) ? -d : (u == 1.0F ? 1.0F + d : u);
				y = facePlane - d;
				z = (v == 0.0F) ? (1.0F + d) : (v == 1.0F ? -d : (1.0F - v));
			}
			default -> throw new IllegalArgumentException("bad face: " + face);
		}

		out[offset] = x;
		out[offset + 1] = y;
		out[offset + 2] = z;
	}
}
