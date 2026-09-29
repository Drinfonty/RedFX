package com.drinfonty.redfx.canvas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Procedural blood splatter stamps onto 16x16 texel canvases.
 * Supports multi-block bleeding across the face plane, organic edge noise, and edge erosion.
 */
public final class BloodSplatter {
	// 16x16 binary masks for splat patterns 1..5 (packed as 16 short bitmasks, one per row)
	private static final short[][] MASKS = {
		// Splat 1
		{
			0x0000, 0x0000, 0x0000, 0x4000, 0x2000, 0x03C0, 0x0FC0, 0x0FE0,
			0x1FE0, 0x0FE0, 0x0FE0, 0x0FC0, 0x0040, 0x0040, 0x0000, 0x0400
		},
		// Splat 2
		{
			0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x07C0, 0x7F00,
			0x3F00, 0x3F00, 0x7F00, 0x3F00, 0x0F80, 0x0400, 0x0000, 0x0800
		},
		// Splat 3
		{
			0x0000, 0x0000, 0x0000, 0x0300, 0x0FC0, 0x0FE0, 0x1FF0, 0x3FF0,
			0x3FF0, 0x1FF0, 0x1FE0, 0x0FE0, 0x0380, 0x0000, 0x0000, 0x0000
		},
		// Splat 4
		{
			0x0000, 0x0000, 0x0000, 0x0000, 0x0100, 0x01C0, 0x07C0, 0x03E0,
			0x07E0, 0x07E0, 0x01C0, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000
		},
		// Splat 5
		{
			0x0000, 0x0000, 0x0000, 0x0000, 0x01C0, 0x07E0, 0x0FF0, 0x0FF0,
			0x0FF0, 0x0FF0, 0x07F0, 0x07E0, 0x0500, 0x0000, 0x0000, 0x0000
		}
	};


	public interface CanvasWriter {
		void setTexel(int globalU, int globalV, int argb);
	}

	@FunctionalInterface
	public interface StagedCanvasWriter {
		void setTexel(int globalU, int globalV, int argb, int stage);
	}

	private BloodSplatter() {
	}

	private record TexelPos(int u, int v) {
	}

	/**
	 * Stamps splatter into the global face plane with initial edge roughness
	 * so edges don't appear blocky or straight.
	 */
	public static void stampGlobal(int globalCenterU, int globalCenterV, int argb, int splatIndex, float scale, CanvasWriter writer) {
		stampGlobal(globalCenterU, globalCenterV, argb, splatIndex, scale, (gu, gv, col, stage) -> writer.setTexel(gu, gv, col));
	}

	/**
	 * Stamps splatter into the global face plane with stage assignments:
	 * Stage 0: Core (deep interior)
	 * Stage 1: Sub-perimeter (inner transition layer)
	 * Stage 2: Outermost perimeter (boundary edge)
	 */
	public static void stampGlobal(int globalCenterU, int globalCenterV, int argb, int splatIndex, float scale, StagedCanvasWriter writer) {
		var random = java.util.concurrent.ThreadLocalRandom.current();
		int pattern = (splatIndex >= 1 && splatIndex <= 5) ? (splatIndex - 1) : random.nextInt(5);
		short[] mask = MASKS[pattern];
		int maskCenter = 7;

		Set<TexelPos> points = new HashSet<>();

		for (int my = 0; my < 16; my++) {
			short row = mask[my];
			if (row == 0) continue;

			for (int mx = 0; mx < 16; mx++) {
				if ((row & (1 << (15 - mx))) != 0) {
					int du = Math.round((mx - maskCenter) * scale);
					int dv = Math.round((my - maskCenter) * scale);

					int gu = globalCenterU + du;
					int gv = globalCenterV + dv;

					points.add(new TexelPos(gu, gv));
				}
			}
		}

		if (points.isEmpty()) {
			points.add(new TexelPos(globalCenterU, globalCenterV));
		}

		// Find perimeter points of the stamped shape (points with fewer than 4 cardinal neighbors)
		List<TexelPos> edgePoints = new ArrayList<>();
		for (TexelPos p : points) {
			if (!points.contains(new TexelPos(p.u - 1, p.v))
				|| !points.contains(new TexelPos(p.u + 1, p.v))
				|| !points.contains(new TexelPos(p.u, p.v - 1))
				|| !points.contains(new TexelPos(p.u, p.v + 1))) {
				edgePoints.add(p);
			}
		}

		// Roughen up perimeter edges by randomly not placing ~25-35% of perimeter points
		if (points.size() > 6 && !edgePoints.isEmpty()) {
			Collections.shuffle(edgePoints, random);
			int numToRemove = Math.max(1, (int) (edgePoints.size() * 0.28f));
			numToRemove = Math.min(numToRemove, points.size() - 3); // ensure core remains
			for (int i = 0; i < numToRemove; i++) {
				points.remove(edgePoints.get(i));
			}
		}

		int rgb = argb & 0xFFFFFF;
		int baseAlpha = (argb >>> 24);
		if (baseAlpha == 0) baseAlpha = 255;
		boolean enableTranslucent = com.drinfonty.redfx.config.RedfxConfig.get().translucentEdges;

		// Distance-to-boundary analysis of existing splat pixels:
		// d1: pixels on the outermost perimeter (have at least one cardinal neighbor outside the splat)
		Set<TexelPos> d1 = new HashSet<>();
		for (TexelPos p : points) {
			if (!points.contains(new TexelPos(p.u - 1, p.v))
				|| !points.contains(new TexelPos(p.u + 1, p.v))
				|| !points.contains(new TexelPos(p.u, p.v - 1))
				|| !points.contains(new TexelPos(p.u, p.v + 1))) {
				d1.add(p);
			}
		}

		// d2: pixels 1 step inward from the perimeter (adjacent to d1, but not on the outer boundary)
		Set<TexelPos> d2 = new HashSet<>();
		for (TexelPos p : points) {
			if (d1.contains(p)) continue;
			if (d1.contains(new TexelPos(p.u - 1, p.v))
				|| d1.contains(new TexelPos(p.u + 1, p.v))
				|| d1.contains(new TexelPos(p.u, p.v - 1))
				|| d1.contains(new TexelPos(p.u, p.v + 1))) {
				d2.add(p);
			}
		}

		boolean hasInterior = points.size() > d1.size();

		int minDistSq = Integer.MAX_VALUE;
		int maxDistSq = Integer.MIN_VALUE;
		if (!hasInterior) {
			for (TexelPos p : points) {
				int d2c = (p.u - globalCenterU) * (p.u - globalCenterU) + (p.v - globalCenterV) * (p.v - globalCenterV);
				if (d2c < minDistSq) minDistSq = d2c;
				if (d2c > maxDistSq) maxDistSq = d2c;
			}
		}

		for (TexelPos p : points) {
			int stage;
			if (hasInterior) {
				if (!d1.contains(p) && !d2.contains(p)) {
					stage = 0; // Deep core
				} else if (d2.contains(p)) {
					stage = 1; // Sub-perimeter
				} else {
					stage = 2; // Outermost edge
				}
			} else {
				int d2c = (p.u - globalCenterU) * (p.u - globalCenterU) + (p.v - globalCenterV) * (p.v - globalCenterV);
				if (minDistSq == maxDistSq || d2c == minDistSq) {
					stage = 0;
				} else if (d2c < maxDistSq) {
					stage = 1;
				} else {
					stage = 2;
				}
			}

			int alpha;
			if (!enableTranslucent) {
				alpha = baseAlpha;
			} else if (stage == 0) {
				// Deep core: 100% of base opacity
				alpha = baseAlpha;
			} else if (stage == 1) {
				// Inner transition edge (1 step inward from perimeter): ~76% opacity
				alpha = (int) Math.round(baseAlpha * (195.0 / 255.0));
			} else {
				// Outermost edge: progressively more transparent farther out
				int cardinalNeighbors = 0;
				if (points.contains(new TexelPos(p.u - 1, p.v))) cardinalNeighbors++;
				if (points.contains(new TexelPos(p.u + 1, p.v))) cardinalNeighbors++;
				if (points.contains(new TexelPos(p.u, p.v - 1))) cardinalNeighbors++;
				if (points.contains(new TexelPos(p.u, p.v + 1))) cardinalNeighbors++;

				if (!hasInterior && cardinalNeighbors >= 3) {
					alpha = (int) Math.round(baseAlpha * (220.0 / 255.0));
				} else if (cardinalNeighbors <= 1) {
					alpha = (int) Math.round(baseAlpha * (95.0 / 255.0));
				} else if (cardinalNeighbors == 2) {
					alpha = (int) Math.round(baseAlpha * (120.0 / 255.0));
				} else {
					alpha = (int) Math.round(baseAlpha * (145.0 / 255.0));
				}
			}

			int col = (alpha << 24) | rgb;
			writer.setTexel(p.u, p.v, col, stage);
		}
	}

	/**
	 * Stamps vertical dripping blood running down a wall (+v direction).
	 * Features an impact splash at (globalCenterU, globalCenterV), streaming rivulets,
	/**
	 * Stamps vertical dripping blood running down a wall (+v direction).
	 * Starts with a smaller, compact impact splatter, picks 1-2 drip points at the bottom of the splatter,
	 * and slowly drips down across 3 stages with random lengths (1-3 pixels) and widths (1-2 pixels).
	 *
	 * Stage 0: Immediate compact impact head
	 * Stage 1: Drip stage 1 (runs down 1-3 px, 1-2 px wide)
	 * Stage 2: Drip stage 2 (runs down another 1-3 px, 1-2 px wide)
	 * Stage 3: Drip stage 3 (runs down another 1-3 px, forming a teardrop bead at tip)
	 */
	/**
	 * Stamps a blood splatter on a vertical wall with downward-dripping teardrop beads.
	 *
	 * Stage 0: Core splatter pixels
	 * Stage 1: Sub-perimeter splatter pixels
	 * Stage 2: Outermost perimeter splatter pixels
	 * Stage 3+: Teardrop bead steps trickling downwards from the bottom of the splatter
	 */
	public static void stampWallDripGlobal(int globalCenterU, int globalCenterV, int argb, int splatIndex, float scale, StagedCanvasWriter writer) {
		long seed = ((long) globalCenterU * 31237L) ^ ((long) globalCenterV * 982451L) ^ ((long) splatIndex * 7919L);
		java.util.Random rand = new java.util.Random(seed);

		int rgb = argb & 0xFFFFFF;
		int baseAlpha = (argb >>> 24);
		if (baseAlpha == 0) baseAlpha = 255;
		boolean enableTranslucent = com.drinfonty.redfx.config.RedfxConfig.get().translucentEdges;

		// 1. Stamp the full organic blood splatter on the wall
		Map<Integer, Integer> maxVPerU = new HashMap<>();
		stampGlobal(globalCenterU, globalCenterV, argb, splatIndex, scale, (gu, gv, col, stage) -> {
			maxVPerU.merge(gu, gv, Math::max);
			writer.setTexel(gu, gv, col, stage);
		});

		if (maxVPerU.isEmpty()) return;

		// 2. Select teardrop bead column(s) from columns that actually contain splatter texels
		List<Integer> availableCols = new ArrayList<>(maxVPerU.keySet());
		Collections.sort(availableCols);
		int k = availableCols.size();
		int hash = Math.abs((globalCenterU * 3127 + globalCenterV * 739 + splatIndex * 19));

		List<Integer> beadColumns = new ArrayList<>();
		if (k <= 5) {
			beadColumns.add(availableCols.get(hash % k));
		} else {
			int b1 = availableCols.get(hash % (k / 2));
			int b2 = availableCols.get(k - 1 - ((hash / 17) % (k / 2)));
			beadColumns.add(b1);
			if (Math.abs(b2 - b1) >= 2) {
				beadColumns.add(b2);
			}
		}

		// 3. Trickle teardrop beads down from the bottom of the splatter
		for (int dripU : beadColumns) {
			int startV = maxVPerU.get(dripU);
			int beadLen = 4 + rand.nextInt(5); // 4..8 pixels long

			for (int step = 1; step <= beadLen; step++) {
				int v = startV + step;
				int alpha;
				if (!enableTranslucent) {
					alpha = baseAlpha;
				} else if (step == beadLen) {
					alpha = (int) Math.round(baseAlpha * (250.0 / 255.0)); // Teardrop bead at tip
				} else if (step == 1) {
					alpha = (int) Math.round(baseAlpha * (230.0 / 255.0));
				} else {
					alpha = (int) Math.round(baseAlpha * (195.0 / 255.0)); // Slender stream
				}
				writer.setTexel(dripU, v, (alpha << 24) | rgb, 2 + step);
			}
		}
	}

	public static boolean stamp(int[] texels, int centerU, int centerV, int argb, int splatIndex, float scale) {
		boolean[] changed = new boolean[1];
		stampGlobal(centerU, centerV, argb, splatIndex, scale, (gu, gv, col) -> {
			if (gu >= 0 && gu < Canvas.SIZE && gv >= 0 && gv < Canvas.SIZE) {
				int idx = gv * Canvas.SIZE + gu;
				int existing = texels[idx];
				int existingAlpha = (existing >>> 24);
				int newAlpha = (col >>> 24);
				if (newAlpha > existingAlpha || (newAlpha == existingAlpha && existing != col)) {
					texels[idx] = col;
					changed[0] = true;
				}
			}
		});
		return changed[0];
	}

	/**
	 * Erodes up to {@code count} edge texels from {@code texels}.
	 * Edge texels are those painted texels adjacent to at least one unpainted texel (or canvas border).
	 * If no edge texels exist, any remaining painted texels are erased.
	 *
	 * @return number of texels erased (0 if canvas was already empty)
	 */
	public static int erode(int[] texels, int count) {
		int[] edgeIndices = new int[Canvas.TEXELS];
		int edgeCount = 0;
		int[] allPainted = new int[Canvas.TEXELS];
		int allCount = 0;

		for (int v = 0; v < Canvas.SIZE; v++) {
			for (int u = 0; u < Canvas.SIZE; u++) {
				int idx = v * Canvas.SIZE + u;
				if (!PaintColor.isPainted(texels[idx])) {
					continue;
				}
				allPainted[allCount++] = idx;

				// Check 4-neighborhood
				boolean isEdge = false;
				if (u == 0 || !PaintColor.isPainted(texels[idx - 1])) isEdge = true;
				else if (u == Canvas.SIZE - 1 || !PaintColor.isPainted(texels[idx + 1])) isEdge = true;
				else if (v == 0 || !PaintColor.isPainted(texels[idx - Canvas.SIZE])) isEdge = true;
				else if (v == Canvas.SIZE - 1 || !PaintColor.isPainted(texels[idx + Canvas.SIZE])) isEdge = true;

				if (isEdge) {
					edgeIndices[edgeCount++] = idx;
				}
			}
		}

		if (allCount == 0) {
			return 0;
		}

		int[] pool = edgeCount > 0 ? edgeIndices : allPainted;
		int poolSize = edgeCount > 0 ? edgeCount : allCount;
		var random = java.util.concurrent.ThreadLocalRandom.current();

		int toErase = Math.min(count, poolSize);
		for (int i = 0; i < toErase; i++) {
			int pick = i + random.nextInt(poolSize - i);
			int chosenIdx = pool[pick];
			pool[pick] = pool[i];
			texels[chosenIdx] = PaintColor.EMPTY;
		}
		return toErase;
	}
}
