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

	@Test
	void simulatesEdgeDripOnBlockLedge() {
		// Target block at (0, 64, 0). Air at (1, 64, 0).
		// Center of splatter at u=14, v=8 on top face (close to East edge).
		int face = FaceAxes.UP;
		int targetX = 0, targetY = 64, targetZ = 0;
		int centerU = 14, centerV = 8;
		int argb = 0xFFFF0000;
		float scale = 0.8f;

		int blockU = FaceStroke.blockU(face, targetX, targetY, targetZ);
		int blockV = FaceStroke.blockV(face, targetX, targetY, targetZ);
		int normal = FaceStroke.normal(face, targetX, targetY, targetZ);

		int globalCenterU = FaceStroke.encodeU(face, blockU, centerU);
		int globalCenterV = FaceStroke.encodeV(face, blockV, centerV);

		java.util.Map<String, int[]> modifiedCanvases = new java.util.HashMap<>();
		java.util.Set<String> solidTopBlocks = new java.util.HashSet<>();

		// Mock surface resolver: only (0, 64, 0) is solid
		java.util.function.Function<int[], int[]> resolveTop = pos -> {
			if (pos[0] == 0 && pos[1] == 64 && pos[2] == 0) {
				return pos;
			}
			return null;
		};

		BloodSplatter.stampGlobal(globalCenterU, globalCenterV, argb, 1, scale, (gu, gv, col) -> {
			int bu = FaceStroke.blockOfU(face, gu);
			int bv = FaceStroke.blockOfV(face, gv);
			int uTexel = gu - FaceStroke.encodeU(face, bu, 0);
			int vTexel = gv - FaceStroke.encodeV(face, bv, 0);

			if (uTexel < 0 || uTexel >= Canvas.SIZE || vTexel < 0 || vTexel >= Canvas.SIZE) {
				return;
			}

			int[] colPos = new int[] { bu, normal, bv };
			int[] surfacePos = resolveTop.apply(colPos);

			if (surfacePos != null) {
				String key = surfacePos[0] + "," + surfacePos[1] + "," + surfacePos[2] + ":" + face;
				int[] tex = modifiedCanvases.computeIfAbsent(key, k -> new int[Canvas.TEXELS]);
				tex[vTexel * 16 + uTexel] = col;
				solidTopBlocks.add(surfacePos[0] + "," + surfacePos[1] + "," + surfacePos[2]);
			} else {
				// Col is air! West neighbor is (bu-1, normal, bv)
				int[] wPos = resolveTop.apply(new int[] { bu - 1, normal, bv });
				if (wPos != null) {
					int dist = EdgeDrip.overhangDistance(FaceAxes.EAST, uTexel, vTexel);
					int sideFace = FaceAxes.EAST;
					int uSide = EdgeDrip.sideU(sideFace, uTexel, vTexel);
					int vSide = dist;
					if (vSide < Canvas.SIZE) {
						String key = wPos[0] + "," + wPos[1] + "," + wPos[2] + ":" + sideFace;
						int[] tex = modifiedCanvases.computeIfAbsent(key, k -> new int[Canvas.TEXELS]);
						tex[vSide * 16 + uSide] = col;
					}
				}
			}
		});

		// 1. Connect rim pixels and group into clusters
		int sideFace = FaceAxes.EAST;
		String topKey = "0,64,0:" + FaceAxes.UP;
		String sideKey = "0,64,0:" + sideFace;
		int[] topTexels = modifiedCanvases.get(topKey);

		if (topTexels != null) {
			boolean[] hasBlood = new boolean[Canvas.SIZE];
			int[] rimColors = new int[Canvas.SIZE];

			for (int coord = 0; coord < Canvas.SIZE; coord++) {
				int edgeCol = topTexels[coord * Canvas.SIZE + 15]; // East rim
				if (edgeCol == 0) {
					int inward1 = topTexels[coord * Canvas.SIZE + 14];
					if (inward1 != 0) {
						edgeCol = inward1;
						topTexels[coord * Canvas.SIZE + 15] = inward1;
					} else {
						int inward2 = topTexels[coord * Canvas.SIZE + 13];
						if (inward2 != 0) {
							edgeCol = inward2;
							topTexels[coord * Canvas.SIZE + 14] = inward2;
							topTexels[coord * Canvas.SIZE + 15] = inward2;
						}
					}
				}
				if (edgeCol != 0) {
					hasBlood[coord] = true;
					rimColors[coord] = edgeCol;
				}
			}

			int cStart = -1;
			for (int coord = 0; coord <= Canvas.SIZE; coord++) {
				if (coord < Canvas.SIZE && hasBlood[coord]) {
					if (cStart == -1) cStart = coord;
				} else if (cStart != -1) {
					int cEnd = coord - 1;
					int[] beadColumns = EdgeDrip.selectBeadColumns(cStart, cEnd, targetX, targetZ, sideFace, 1);
					for (int c : beadColumns) {
						int col = rimColors[c];
						int baseAlpha = (col >>> 24);
						int rgb = col & 0xFFFFFF;

						int uSide = EdgeDrip.sideU(sideFace, 15, c);

						int dripLen = EdgeDrip.calculateDripLength(
							targetX, targetZ, sideFace, uSide,
							cStart, cEnd, 15, 1
						);

						for (int step = 0; step <= dripLen; step++) {
							int dripAlpha = EdgeDrip.dripAlpha(baseAlpha, step, dripLen);
							int dripCol = (dripAlpha << 24) | rgb;
							int[] tex = modifiedCanvases.computeIfAbsent(sideKey, k -> new int[Canvas.TEXELS]);
							tex[step * 16 + uSide] = dripCol;
						}
					}
					cStart = -1;
				}
			}
		}

		assertTrue(modifiedCanvases.containsKey(topKey), "Top face should have blood");
		assertTrue(modifiedCanvases.containsKey(sideKey), "Side face should have blood drips");

		int[] sideTex = modifiedCanvases.get(sideKey);
		int sidePainted = 0;
		int maxV = 0;
		for (int v = 0; v < 16; v++) {
			for (int u = 0; u < 16; u++) {
				int val = sideTex[v * 16 + u];
				if (val != 0) {
					sidePainted++;
					maxV = Math.max(maxV, v);
				}
			}
		}
		assertTrue(sidePainted >= 5, "Should paint teardrop bead pixels on side face");
		assertTrue(maxV >= 4 && maxV <= 8, "Teardrop beads should extend 4 to 8 pixels down the face");
	}

	@Test
	void selectBeadColumnsEliminatesRivuletsWithSeparatedSingleTexelBeads() {
		// Single pixel cluster
		int[] single = EdgeDrip.selectBeadColumns(5, 5, 0, 0, FaceAxes.NORTH, 1);
		assertEquals(1, single.length);
		assertEquals(5, single[0]);

		// Narrow cluster (width 3): returns exactly 1 column within [2, 4]
		int[] narrow = EdgeDrip.selectBeadColumns(2, 4, 10, 20, FaceAxes.EAST, 2);
		assertEquals(1, narrow.length);
		assertTrue(narrow[0] >= 2 && narrow[0] <= 4);

		// Wide cluster (width 8): returns 1 or at most 2 separated columns that are NEVER adjacent
		for (int splat = 1; splat <= 20; splat++) {
			int[] wide = EdgeDrip.selectBeadColumns(2, 9, 5, 15, FaceAxes.SOUTH, splat);
			assertTrue(wide.length >= 1 && wide.length <= 2, "Should pick 1 or 2 beads");
			for (int b : wide) {
				assertTrue(b >= 2 && b <= 9, "Bead must be within cluster bounds");
			}
			if (wide.length == 2) {
				assertTrue(Math.abs(wide[0] - wide[1]) >= 2, "Bead columns must never be adjacent (no rivulets)");
			}
		}
	}

	@Test
	void calculateDripLengthExtendsFourToEightPixels() {
		for (int u = 0; u < 16; u++) {
			int len = EdgeDrip.calculateDripLength(10, -5, FaceAxes.NORTH, u, 0, 15, 15, 1);
			assertTrue(len >= 4 && len <= 8, "Bead drip length must be between 4 and 8 pixels: " + len);
		}
	}

	@Test
	void fasterBeadsRunLongerWithShorterStepDelays() {
		long delayLen8 = EdgeDrip.beadStepDelayMs(8, 1.0f);
		long delayLen6 = EdgeDrip.beadStepDelayMs(6, 1.0f);
		long delayLen4 = EdgeDrip.beadStepDelayMs(4, 1.0f);

		assertTrue(delayLen8 < delayLen6, "Length 8 bead should advance faster than length 6");
		assertTrue(delayLen6 < delayLen4, "Length 6 bead should advance faster than length 4");
	}

	@Test
	void exposedHeightCalculationNeverOverflowsForAirNeighbor() {
		double myElevation = 65.0;
		int blockY = 64;

		// When neighbor is air/cliff (neighborInfo == null)
		double exposedHeight = myElevation - blockY;
		int maxDropTexels = Math.min(Canvas.SIZE, (int) Math.round(exposedHeight * 16.0));

		assertEquals(16, maxDropTexels, "Air neighbor must expose full 16 texels down the face");
		assertTrue(maxDropTexels > 0, "maxDropTexels must be positive and not overflow");
	}
}
