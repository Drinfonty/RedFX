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
}
