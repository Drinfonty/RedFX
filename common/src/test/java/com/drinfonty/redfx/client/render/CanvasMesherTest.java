package com.drinfonty.redfx.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.drinfonty.redfx.canvas.Canvas;
import com.drinfonty.redfx.canvas.FaceAxes;
import com.drinfonty.redfx.canvas.PaintColor;

import org.junit.jupiter.api.Test;

class CanvasMesherTest {
	private static int[] rasterise(List<PaintQuad> quads) {
		int[] grid = new int[Canvas.TEXELS];

		for (PaintQuad quad : quads) {
			assertTrue(quad.minU() >= 0 && quad.maxU() <= Canvas.SIZE, "u out of range");
			assertTrue(quad.minV() >= 0 && quad.maxV() <= Canvas.SIZE, "v out of range");
			assertTrue(quad.widthTexels() > 0 && quad.heightTexels() > 0, "degenerate quad");
			assertTrue(PaintColor.isPainted(quad.argb()), "an unpainted quad was emitted");

			for (int pv = quad.minV(); pv < quad.maxV(); pv++) {
				for (int pu = quad.minU(); pu < quad.maxU(); pu++) {
					int index = pv * Canvas.SIZE + pu;
					assertEquals(0, grid[index], "quads overlap at " + pu + "," + pv);
					grid[index] = quad.argb();
				}
			}
		}

		return grid;
	}

	private static void assertCoversExactly(int[] texels) {
		List<PaintQuad> quads = CanvasMesher.mesh(texels, FaceAxes.NORTH);
		assertTrue(quads.size() <= Canvas.TEXELS, "more quads than texels");
		for (int i = 0; i < Canvas.TEXELS; i++) {
			assertEquals(PaintColor.normalise(texels[i]), rasterise(quads)[i], "texel " + i);
		}
	}

	@Test
	void emptyCanvasProducesNothing() {
		assertTrue(CanvasMesher.mesh(new int[Canvas.TEXELS], FaceAxes.UP).isEmpty());
	}

	@Test
	void solidCanvasProducesOneQuad() {
		int[] texels = new int[Canvas.TEXELS];
		for (int i = 0; i < Canvas.TEXELS; i++) {
			texels[i] = 0xFFCC0000;
		}

		List<PaintQuad> quads = CanvasMesher.mesh(texels, FaceAxes.UP);
		assertEquals(1, quads.size());
		PaintQuad quad = quads.get(0);
		assertEquals(0, quad.minU());
		assertEquals(0, quad.minV());
		assertEquals(16, quad.maxU());
		assertEquals(16, quad.maxV());
		assertEquals(0xFFCC0000, quad.argb());
	}

	@Test
	void arbitraryPatternRasterisesEquivalently() {
		int[] texels = new int[Canvas.TEXELS];
		texels[5 * 16 + 5] = 0xFFFF0000;
		texels[5 * 16 + 6] = 0xFFFF0000;
		texels[6 * 16 + 5] = 0xFFFF0000;
		texels[6 * 16 + 6] = 0xFFFF0000;
		assertCoversExactly(texels);
	}

	@Test
	void checkerboardPatternProducesMaximalQuadsWithoutOverlap() {
		int[] texels = new int[Canvas.TEXELS];
		int count = 0;
		for (int v = 0; v < Canvas.SIZE; v++) {
			for (int u = 0; u < Canvas.SIZE; u++) {
				if ((u + v) % 2 == 0) {
					texels[v * Canvas.SIZE + u] = 0xFFFF0000;
					count++;
				}
			}
		}
		List<PaintQuad> quads = CanvasMesher.mesh(texels, FaceAxes.NORTH);
		assertEquals(count, quads.size(), "Isolated checkerboard squares should each be 1 quad");
		assertCoversExactly(texels);
	}

	@Test
	void hollowRectMeshesCorrectly() {
		int[] texels = new int[Canvas.TEXELS];
		// 10x10 outer box at (3,3) to (12,12), hollow 6x6 inside at (5,5) to (10,10)
		for (int v = 3; v <= 12; v++) {
			for (int u = 3; u <= 12; u++) {
				if (v < 5 || v > 10 || u < 5 || u > 10) {
					texels[v * Canvas.SIZE + u] = 0xFF880000;
				}
			}
		}
		assertCoversExactly(texels);
	}

	@Test
	void multipleColorsMergeSeparately() {
		int[] texels = new int[Canvas.TEXELS];
		int red = 0xFFFF0000;
		int darkRed = 0xFF880000;
		// Left half red, right half dark red
		for (int v = 0; v < Canvas.SIZE; v++) {
			for (int u = 0; u < Canvas.SIZE; u++) {
				texels[v * Canvas.SIZE + u] = (u < 8) ? red : darkRed;
			}
		}
		List<PaintQuad> quads = CanvasMesher.mesh(texels, FaceAxes.UP);
		assertEquals(2, quads.size(), "Left and right halves of different colors should merge into 2 quads");
		assertCoversExactly(texels);
	}

	@Test
	void rejectsInvalidTexelArraySize() {
		org.junit.jupiter.api.Assertions.assertThrows(
			IllegalArgumentException.class,
			() -> CanvasMesher.mesh(new int[10], FaceAxes.UP)
		);
	}
}
