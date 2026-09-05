package com.drinfonty.redfx.canvas;

/**
 * The blood paint on one face of one block: a 16x16 grid of ARGB texels,
 * plus a timestamp indicating when this canvas should begin fading/eroding,
 * and when the next erosion step should occur.
 *
 * Canvases are replaced rather than mutated in place once published, making
 * reads from chunk-mesher threads lock-free and thread-safe.
 */
public final class Canvas {
	public static final int SIZE = 16;
	public static final int TEXELS = SIZE * SIZE;

	private final int[] texels;
	private final long fadeStartTimeMs;
	private final long nextErodeTimeMs;

	public Canvas(int[] texels, long fadeStartTimeMs, long nextErodeTimeMs) {
		if (texels.length != TEXELS) {
			throw new IllegalArgumentException("canvas must be " + TEXELS + " texels");
		}
		this.texels = texels;
		this.fadeStartTimeMs = fadeStartTimeMs;
		this.nextErodeTimeMs = nextErodeTimeMs;
	}

	public Canvas(int[] texels, long fadeStartTimeMs) {
		this(texels, fadeStartTimeMs, fadeStartTimeMs);
	}

	public static Canvas empty() {
		return new Canvas(new int[TEXELS], 0L, 0L);
	}

	public int[] texels() {
		return texels;
	}

	public long fadeStartTimeMs() {
		return fadeStartTimeMs;
	}

	public long nextErodeTimeMs() {
		return nextErodeTimeMs;
	}

	public boolean isEmpty() {
		for (int t : texels) {
			if (PaintColor.isPainted(t)) {
				return false;
			}
		}
		return true;
	}

	public Canvas copy() {
		return new Canvas(texels.clone(), fadeStartTimeMs, nextErodeTimeMs);
	}
}
