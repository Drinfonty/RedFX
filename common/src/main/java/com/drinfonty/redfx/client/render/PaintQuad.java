package com.drinfonty.redfx.client.render;

import com.drinfonty.redfx.canvas.Canvas;
import com.drinfonty.redfx.canvas.FaceAxes;

/**
 * A single painted rectangle on a block face: integer texel bounds, which face it sits on,
 * and the 32-bit ARGB colour it carries.
 */
public record PaintQuad(int face, int minU, int minV, int maxU, int maxV, int argb) {
	public PaintQuad {
		if (!FaceAxes.isValidFace(face)) {
			throw new IllegalArgumentException("bad face: " + face);
		}
		if (minU < 0 || maxU > Canvas.SIZE || minU >= maxU) {
			throw new IllegalArgumentException("invalid u range: " + minU + ".." + maxU);
		}
		if (minV < 0 || maxV > Canvas.SIZE || minV >= maxV) {
			throw new IllegalArgumentException("invalid v range: " + minV + ".." + maxV);
		}
	}

	public int widthTexels() {
		return maxU - minU;
	}

	public int heightTexels() {
		return maxV - minV;
	}
}
