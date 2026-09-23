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

	@Test
	void stampsWallDripWithDownwardRivulets() {
		java.util.List<Integer> vCoords = new java.util.ArrayList<>();
		java.util.Map<Integer, java.util.List<Integer>> stageVs = new java.util.HashMap<>();
		int red = 0xFFFF0000;
		int impactV = 4;

		BloodSplatter.stampWallDripGlobal(7, impactV, red, 1, 1.0f, (u, v, argb, stage) -> {
			vCoords.add(v);
			stageVs.computeIfAbsent(stage, k -> new java.util.ArrayList<>()).add(v);
		});

		int minV = vCoords.stream().min(Integer::compareTo).orElse(impactV);
		int maxV = vCoords.stream().max(Integer::compareTo).orElse(impactV);

		assertTrue(maxV - impactV >= 4, "Wall drip should extend downwards below impact point (+v)");
		assertTrue(maxV - minV >= 4, "Wall drip total vertical extent should span multiple rows");

		// Verify stage 0 is at or near the impact point
		assertTrue(stageVs.containsKey(0), "Should contain stage 0 impact head");
		int minStage0V = stageVs.get(0).stream().min(Integer::compareTo).orElse(0);
		int maxStage0V = stageVs.get(0).stream().max(Integer::compareTo).orElse(0);
		assertTrue(minStage0V <= impactV && maxStage0V <= impactV + 2, "Stage 0 should be concentrated at impact head");

		// Verify all 3 drip stages are present
		assertTrue(stageVs.containsKey(1), "Should contain drip stage 1");
		assertTrue(stageVs.containsKey(2), "Should contain drip stage 2");
		assertTrue(stageVs.containsKey(3), "Should contain drip stage 3");

		// Verify highest stage contains the lowest dripping pixels
		int maxStage = stageVs.keySet().stream().max(Integer::compareTo).orElse(0);
		int maxStageV = stageVs.get(maxStage).stream().max(Integer::compareTo).orElse(0);
		assertEquals(maxV, maxStageV, "Highest stage should reach the bottom-most teardrop pixels");
	}

	@Test
	void stampsWallDripAllPatternsWithoutCrashing() {
		int red = 0xFFFF0000;
		for (int pattern = 1; pattern <= 10; pattern++) {
			java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(0);
			BloodSplatter.stampWallDripGlobal(8, 4, red, pattern, 1.0f, (u, v, argb, stage) -> {
				count.incrementAndGet();
			});
			assertTrue(count.get() >= 5, "Wall drip pattern " + pattern + " should produce dripping pixels");
		}
	}
}
