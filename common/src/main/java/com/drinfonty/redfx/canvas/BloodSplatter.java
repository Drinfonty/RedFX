package com.drinfonty.redfx.canvas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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

	private BloodSplatter() {
	}

	private record TexelPos(int u, int v) {
	}

	/**
	 * Stamps splatter into the global face plane with initial edge roughness
	 * so edges don't appear blocky or straight.
	 */
	public static void stampGlobal(int globalCenterU, int globalCenterV, int argb, int splatIndex, float scale, CanvasWriter writer) {
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
		boolean enableTranslucent = com.drinfonty.redfx.config.RedfxConfig.get().translucentEdges;

		if (!enableTranslucent) {
			for (TexelPos p : points) {
				writer.setTexel(p.u, p.v, 0xFF000000 | rgb);
			}
			return;
		}

		// Distance-to-boundary analysis of existing splat pixels (no extra pixels added):
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

		for (TexelPos p : points) {
			int alpha;
			if (!d1.contains(p) && !d2.contains(p)) {
				// Deep core: 100% opaque
				alpha = 255;
			} else if (d2.contains(p)) {
				// Inner transition edge (1 step inward from perimeter): ~76% opacity
				alpha = 195;
			} else {
				// Outermost edge (d1): progressively more transparent farther out
				int cardinalNeighbors = 0;
				if (points.contains(new TexelPos(p.u - 1, p.v))) cardinalNeighbors++;
				if (points.contains(new TexelPos(p.u + 1, p.v))) cardinalNeighbors++;
				if (points.contains(new TexelPos(p.u, p.v - 1))) cardinalNeighbors++;
				if (points.contains(new TexelPos(p.u, p.v + 1))) cardinalNeighbors++;

				if (!hasInterior && cardinalNeighbors >= 3) {
					// In a tiny speck with no core, keep center pixels mostly solid
					alpha = 220;
				} else if (cardinalNeighbors <= 1) {
					// Outermost tips / spurs: most transparent (~37% opacity)
					alpha = 95;
				} else if (cardinalNeighbors == 2) {
					// Corners / exposed edges (~47% opacity)
					alpha = 120;
				} else {
					// Perimeter borders (~57% opacity)
					alpha = 145;
				}
			}

			int col = (alpha << 24) | rgb;
			writer.setTexel(p.u, p.v, col);
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
