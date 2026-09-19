package com.drinfonty.redfx.canvas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EdgeDripTest {
	@Test
	void mapsSideUCorrectlyForAllFourFaces() {
		// Test u = 3, v = 5
		assertEquals(12, EdgeDrip.sideU(FaceAxes.NORTH, 3, 5), "North sideU");
		assertEquals(3, EdgeDrip.sideU(FaceAxes.SOUTH, 3, 5), "South sideU");
		assertEquals(5, EdgeDrip.sideU(FaceAxes.WEST, 3, 5), "West sideU");
		assertEquals(10, EdgeDrip.sideU(FaceAxes.EAST, 3, 5), "East sideU");
	}

	@Test
	void clampsSideUWithinBounds() {
		assertEquals(0, EdgeDrip.sideU(FaceAxes.NORTH, 20, 5));
		assertEquals(15, EdgeDrip.sideU(FaceAxes.NORTH, -5, 5));
		assertEquals(15, EdgeDrip.sideU(FaceAxes.SOUTH, 20, 5));
		assertEquals(0, EdgeDrip.sideU(FaceAxes.SOUTH, -5, 5));
	}

	@Test
	void calculatesOverhangDistanceCorrectly() {
		// East edge (overhang into block to the east, u increases from 0)
		assertEquals(0, EdgeDrip.overhangDistance(FaceAxes.EAST, 0, 7));
		assertEquals(3, EdgeDrip.overhangDistance(FaceAxes.EAST, 3, 7));

		// West edge (overhang into block to the west, u decreases towards 15)
		assertEquals(0, EdgeDrip.overhangDistance(FaceAxes.WEST, 15, 7));
		assertEquals(3, EdgeDrip.overhangDistance(FaceAxes.WEST, 12, 7));

		// South edge (overhang into block to the south, v increases from 0)
		assertEquals(0, EdgeDrip.overhangDistance(FaceAxes.SOUTH, 7, 0));
		assertEquals(4, EdgeDrip.overhangDistance(FaceAxes.SOUTH, 7, 4));

		// North edge (overhang into block to the north, v decreases towards 15)
		assertEquals(0, EdgeDrip.overhangDistance(FaceAxes.NORTH, 7, 15));
		assertEquals(4, EdgeDrip.overhangDistance(FaceAxes.NORTH, 7, 11));
	}

	@Test
	void calculatesOrganicDripLengthAndAlphaDecay() {
		for (int u = 0; u < 16; u++) {
			int length = EdgeDrip.extraDripLength(10, -5, FaceAxes.NORTH, u, 1);
			assertTrue(length >= 1 && length <= 3, "drip length should be between 1 and 3");
		}

		int baseAlpha = 255;
		int step1 = EdgeDrip.dripAlpha(baseAlpha, 1);
		int step2 = EdgeDrip.dripAlpha(baseAlpha, 2);
		int step3 = EdgeDrip.dripAlpha(baseAlpha, 3);

		assertTrue(step1 > step2, "alpha step1 > step2");
		assertTrue(step2 > step3, "alpha step2 > step3");
		assertTrue(step3 >= 60, "alpha should maintain minimum visibility");
	}
}
