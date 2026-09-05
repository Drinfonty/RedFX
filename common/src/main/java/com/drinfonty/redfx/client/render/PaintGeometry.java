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

	public static float[] corners(PaintQuad quad, float[] out) {
		return corners(quad, out, 1.0F);
	}

	public static float[] corners(PaintQuad quad, float[] out, float surfaceY) {
		if (out.length < 12) {
			throw new IllegalArgumentException("need room for 12 floats");
		}

		float u0 = quad.minU() * TEXEL;
		float u1 = quad.maxU() * TEXEL;
		float v0 = quad.minV() * TEXEL;
		float v1 = quad.maxV() * TEXEL;

		// (u, v) walked anticlockwise as seen from outside the face:
		// bottom-left, bottom-right, top-right, top-left.
		write(out, 0, quad.face(), u0, v1, surfaceY);
		write(out, 3, quad.face(), u1, v1, surfaceY);
		write(out, 6, quad.face(), u1, v0, surfaceY);
		write(out, 9, quad.face(), u0, v0, surfaceY);

		return out;
	}

	private static void write(float[] out, int offset, int face, float u, float v, float surfaceY) {
		float x;
		float y;
		float z;
		float d = DECAL_OFFSET;

		switch (face) {
			case FaceAxes.NORTH -> {
				x = 1.0F - u;
				y = 1.0F - v;
				z = -d;
			}
			case FaceAxes.SOUTH -> {
				x = u;
				y = 1.0F - v;
				z = 1.0F + d;
			}
			case FaceAxes.WEST -> {
				x = -d;
				y = 1.0F - v;
				z = u;
			}
			case FaceAxes.EAST -> {
				x = 1.0F + d;
				y = 1.0F - v;
				z = 1.0F - u;
			}
			case FaceAxes.UP -> {
				x = u;
				y = surfaceY + d;
				z = v;
			}
			case FaceAxes.DOWN -> {
				x = u;
				y = -d;
				z = 1.0F - v;
			}
			default -> throw new IllegalArgumentException("bad face: " + face);
		}

		out[offset] = x;
		out[offset + 1] = y;
		out[offset + 2] = z;
	}
}
