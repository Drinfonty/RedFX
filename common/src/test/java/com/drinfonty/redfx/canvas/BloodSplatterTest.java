package com.drinfonty.redfx.canvas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BloodSplatterTest {
	@Test
	void stampsOntoCanvasAndModifiesTexels() {
		int[] texels = new int[Canvas.TEXELS];
		int red = 0xFFFF0000;
		boolean stamped = BloodSplatter.stamp(texels, 7, 7, red, 1, 1.0f);

		assertTrue(stamped);
		assertEquals(red, texels[7 * 16 + 7]);

		int count = 0;
		for (int t : texels) {
			if (t == red) {
				count++;
			}
		}
		assertTrue(count > 10, "Expected a splatter cluster of painted texels");
	}

	@Test
	void stampsNearEdgeWithoutThrowing() {
		int[] texels = new int[Canvas.TEXELS];
		int red = 0xFFFF0000;
		boolean stamped = BloodSplatter.stamp(texels, 0, 0, red, 2, 1.0f);
		assertTrue(stamped);
		int painted = 0;
		for (int t : texels) {
			if (t == red) painted++;
		}
		assertTrue(painted > 0);
	}

	@Test
	void erodesEdgeTexelsGraduallyUntilEmpty() {
		int[] texels = new int[Canvas.TEXELS];
		int red = 0xFFFF0000;
		BloodSplatter.stamp(texels, 7, 7, red, 1, 1.0f);

		int initialPainted = 0;
		for (int t : texels) {
			if (t == red) initialPainted++;
		}
		assertTrue(initialPainted > 0);

		int erased = BloodSplatter.erode(texels, 3);
		assertEquals(3, erased);

		int remaining = 0;
		for (int t : texels) {
			if (t == red) remaining++;
		}
		assertEquals(initialPainted - 3, remaining);
	}

	@Test
	void erodeReturnsZeroOnEmptyCanvas() {
		int[] texels = new int[Canvas.TEXELS];
		assertEquals(0, BloodSplatter.erode(texels, 5));
	}

	@Test
	void erodeCompletelyCleansCanvasWhenCountExceedsPainted() {
		int[] texels = new int[Canvas.TEXELS];
		int red = 0xFFFF0000;
		BloodSplatter.stamp(texels, 7, 7, red, 1, 1.0f);

		// Erode in chunks until everything is gone
		while (true) {
			int erased = BloodSplatter.erode(texels, 20);
			if (erased == 0) break;
		}

		for (int t : texels) {
			assertEquals(0, t, "All texels should be eroded away");
		}
	}

	@Test
	void stampsAllPatternsWithoutCrashing() {
		int red = 0xFFFF0000;
		for (int pattern = 1; pattern <= 5; pattern++) {
			int[] texels = new int[Canvas.TEXELS];
			boolean stamped = BloodSplatter.stamp(texels, 8, 8, red, pattern, 1.0f);
			assertTrue(stamped, "Pattern " + pattern + " should stamp texels");
		}
	}

	@Test
	void stampGlobalWritesAcrossChunkBoundaries() {
		java.util.Map<Long, int[]> canvasMap = new java.util.HashMap<>();
		int red = 0xFFFF0000;

		// Splatter centered directly on boundary between block 0 and block 1
		int globalU = 15;
		int globalV = 15;

		BloodSplatter.stampGlobal(globalU, globalV, red, 3, 1.0f, (u, v, argb) -> {
			int blockU = Math.floorDiv(u, 16);
			int blockV = Math.floorDiv(v, 16);
			long key = (((long) blockU) << 32) | (((long) blockV) & 0xFFFFFFFFL);
			int[] tex = canvasMap.computeIfAbsent(key, k -> new int[Canvas.TEXELS]);
			int localU = Math.floorMod(u, 16);
			int localV = Math.floorMod(v, 16);
			tex[localV * 16 + localU] = argb;
		});

		assertTrue(canvasMap.size() > 1, "Splatter on boundary should bleed into multiple adjacent block canvases");
	}
}
