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

		Set<TexelPos> points = new HashSet<>();
		Set<TexelPos> blob = generateOrganicBlob(globalCenterU, globalCenterV, splatIndex, scale, random);
		points.addAll(blob);

		// Roughen the main blob's rim lightly so it reads as pixel-art speckle rather than a smooth disc
		if (blob.size() > 10) {
			List<TexelPos> rim = new ArrayList<>();
			for (TexelPos p : blob) {
				if (!blob.contains(new TexelPos(p.u - 1, p.v))
					|| !blob.contains(new TexelPos(p.u + 1, p.v))
					|| !blob.contains(new TexelPos(p.u, p.v - 1))
					|| !blob.contains(new TexelPos(p.u, p.v + 1))) {
					rim.add(p);
				}
			}
			Collections.shuffle(rim, random);
			int numToRemove = Math.min((int) (rim.size() * 0.15f), points.size() - 3);
			for (int i = 0; i < numToRemove; i++) {
				points.remove(rim.get(i));
			}
		}

		addSatellites(points, globalCenterU, globalCenterV, scale, random);
		points.add(new TexelPos(globalCenterU, globalCenterV));

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

		Set<TexelPos> d3 = new HashSet<>();
		for (TexelPos p : points) {
			if (!d1.contains(p) && !d2.contains(p)) {
				d3.add(p);
			}
		}

		boolean hasInterior = !d3.isEmpty() || !d2.isEmpty();

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
			if (!d3.isEmpty()) {
				if (d3.contains(p)) {
					stage = 0; // Deep core
				} else if (d2.contains(p)) {
					stage = 1; // Sub-perimeter
				} else {
					stage = 2; // Outermost edge
				}
			} else if (!d2.isEmpty()) {
				if (d2.contains(p)) {
					stage = 0; // Innermost layer acts as core
				} else {
					stage = (p.u == globalCenterU && p.v == globalCenterV) ? 1 : 2;
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

			int col = (alpha << 24) | shade(rgb, p.u, p.v);
			writer.setTexel(p.u, p.v, col, stage);
		}
	}

	/**
	 * Builds an irregular, slightly elongated blob whose outline is perturbed by low-frequency
	 * angular harmonics plus per-texel jitter, so no two splats share the same silhouette.
	 */
	private static Set<TexelPos> generateOrganicBlob(int cu, int cv, int splatIndex, float scale, Random random) {
		Set<TexelPos> out = new HashSet<>();
		double radius = Math.max(1.2, (2.8 + random.nextDouble() * 1.6) * scale);
		double elong = 1.0 + random.nextDouble() * 0.5;
		double angle = random.nextDouble() * Math.PI * 2.0;
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);

		int harmonics = 3;
		double[] amp = new double[harmonics];
		double[] phase = new double[harmonics];
		for (int k = 0; k < harmonics; k++) {
			amp[k] = (0.10 + random.nextDouble() * 0.14) / (k + 1);
			phase[k] = random.nextDouble() * Math.PI * 2.0;
		}

		int bound = (int) Math.ceil(radius * elong * 1.5) + 1;
		for (int dv = -bound; dv <= bound; dv++) {
			for (int du = -bound; du <= bound; du++) {
				double x = (du * cos + dv * sin) / elong;
				double y = -du * sin + dv * cos;
				double r = Math.sqrt(x * x + y * y);
				double theta = Math.atan2(y, x);
				double edge = 1.0;
				for (int k = 0; k < harmonics; k++) {
					edge += amp[k] * Math.sin((k + 2) * theta + phase[k]);
				}
				double jitter = (random.nextDouble() - 0.5) * 0.9;
				if (r + jitter <= radius * edge) {
					out.add(new TexelPos(cu + du, cv + dv));
				}
			}
		}
		return out;
	}

	/**
	 * Scatters small satellite droplets around the main blob, some joined to it by thin tendrils,
	 * which gives the spattered, "flung" look and helps neighbouring splats bridge together.
	 */
	private static void addSatellites(Set<TexelPos> points, int cu, int cv, float scale, Random random) {
		double radius = Math.max(0.7, 3.0 * scale);
		int count = (int) Math.round((1 + random.nextDouble() * 4) * Math.min(1.5, scale + 0.3));
		for (int i = 0; i < count; i++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			double dist = radius * (1.2 + random.nextDouble() * 1.0);
			int su = cu + (int) Math.round(Math.cos(a) * dist);
			int sv = cv + (int) Math.round(Math.sin(a) * dist);
			int size = random.nextDouble() < 0.35 * Math.min(1.0, scale) ? 1 : 0;
			for (int dv = -size; dv <= size; dv++) {
				for (int du = -size; du <= size; du++) {
					if (size == 0 || Math.abs(du) + Math.abs(dv) <= size) {
						points.add(new TexelPos(su + du, sv + dv));
					}
				}
			}
			if (random.nextDouble() < 0.4) {
				int steps = (int) Math.ceil(dist);
				for (int s = 1; s < steps; s++) {
					double t = s / (double) steps;
					points.add(new TexelPos(cu + (int) Math.round(Math.cos(a) * dist * t),
						cv + (int) Math.round(Math.sin(a) * dist * t)));
				}
			}
		}
	}

	/**
	 * Mottles colour by world-plane texel coordinate (not per-splat), so where splats overlap
	 * they continue the same pattern and read as one merged pool.
	 */
	private static int shade(int rgb, int gu, int gv) {
		double n = 0.65 * smoothNoise(gu / 3.0, gv / 3.0) + 0.35 * hash01(gu, gv);
		double f = 0.80 + 0.28 * n;
		int r = (int) Math.min(255, Math.round(((rgb >> 16) & 0xFF) * f));
		int g = (int) Math.min(255, Math.round(((rgb >> 8) & 0xFF) * f));
		int b = (int) Math.min(255, Math.round((rgb & 0xFF) * f));
		return (r << 16) | (g << 8) | b;
	}

	private static double smoothNoise(double x, double y) {
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		double fx = x - x0;
		double fy = y - y0;
		fx = fx * fx * (3 - 2 * fx);
		fy = fy * fy * (3 - 2 * fy);
		double a = hash01(x0 * 7919, y0 * 104729);
		double b = hash01((x0 + 1) * 7919, y0 * 104729);
		double c = hash01(x0 * 7919, (y0 + 1) * 104729);
		double d = hash01((x0 + 1) * 7919, (y0 + 1) * 104729);
		return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
	}

	private static double hash01(int x, int y) {
		int h = x * 374761393 + y * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
	}

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
