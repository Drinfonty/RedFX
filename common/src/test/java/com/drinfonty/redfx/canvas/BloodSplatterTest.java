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
		assertEquals(0x00FF0000, texels[7 * 16 + 7] & 0x00FFFFFF);
		assertTrue(PaintColor.isPainted(texels[7 * 16 + 7]));

		int count = 0;
		for (int t : texels) {
			if (PaintColor.isPainted(t)) {
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
			if (PaintColor.isPainted(t)) painted++;
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
			if (PaintColor.isPainted(t)) initialPainted++;
		}
		assertTrue(initialPainted > 0);

		int erased = BloodSplatter.erode(texels, 3);
		assertEquals(3, erased);

		int remaining = 0;
		for (int t : texels) {
			if (PaintColor.isPainted(t)) remaining++;
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

	@Test
	void stampsTranslucentEdgePixelsWithDecreasingAlpha() {
		int[] texels = new int[Canvas.TEXELS];
		int red = 0xFFFF0000;
		BloodSplatter.stamp(texels, 7, 7, red, 1, 1.0f);

		int opaqueCount = 0;
		int subPerimeterCount = 0;
		int outerEdgeCount = 0;

		for (int t : texels) {
			if (!PaintColor.isPainted(t)) continue;
			int alpha = (t >>> 24);
			if (alpha == 255) {
				opaqueCount++;
			} else if (alpha == 195) {
				subPerimeterCount++;
			} else if (alpha >= 95 && alpha <= 145) {
				outerEdgeCount++;
			}
		}

		assertTrue(opaqueCount > 0, "Should have solid core pixels");
		assertTrue(outerEdgeCount > 0, "Should have translucent outer edge pixels (alpha ~95-145)");
	}

	@Test
	void stampsStagedSplatterWithCoreSubPerimeterAndOuterEdgeStages() {
		java.util.List<Integer> stages = new java.util.ArrayList<>();
		java.util.Map<Integer, Integer> stageAlphas = new java.util.HashMap<>();
		int red = 0xFFFF0000;

		BloodSplatter.stampGlobal(7, 7, red, 1, 1.0f, (u, v, argb, stage) -> {
			stages.add(stage);
			stageAlphas.put(stage, (argb >>> 24));
		});

		assertTrue(stages.contains(0), "Splatter must contain stage 0 (core)");
		assertTrue(stages.contains(1), "Splatter must contain stage 1 (sub-perimeter)");
		assertTrue(stages.contains(2), "Splatter must contain stage 2 (outermost perimeter)");

		assertEquals(255, stageAlphas.get(0), "Stage 0 core pixels must be fully opaque");
		assertEquals(195, stageAlphas.get(1), "Stage 1 sub-perimeter pixels must be ~76% opaque");
		assertTrue(stageAlphas.get(2) < 195, "Stage 2 outer edge pixels must be more translucent");
	}

	@Test
	void stampsSmallSpeckWithImmediateCoreStage() {
		java.util.List<Integer> stages = new java.util.ArrayList<>();
		int red = 0xFFFF0000;

		// Extremely small scale (0.2x) to create a tiny 1-3 pixel speck
		BloodSplatter.stampGlobal(7, 7, red, 4, 0.2f, (u, v, argb, stage) -> {
			stages.add(stage);
		});

		assertTrue(stages.contains(0), "Even small specks must always have a stage 0 (immediate core) pixel");
	}
}
