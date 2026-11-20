package com.drinfonty.redfx.canvas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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

	private static final class Scratch {
		byte[] grid = new byte[40 * 40];
		byte[] layer = new byte[40 * 40];
		int[] activeIdx = new int[40 * 40];
		int[] rimBuf = new int[40 * 40];
		final int[] erodeEdge = new int[Canvas.TEXELS];
		final int[] erodeAll = new int[Canvas.TEXELS];

		void ensureGrid(int cells) {
			if (grid.length < cells) {
				grid = new byte[cells];
				layer = new byte[cells];
				activeIdx = new int[cells];
				rimBuf = new int[cells];
			}
			java.util.Arrays.fill(grid, 0, cells, (byte) 0);
		}
	}

	private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

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
		stampGlobal(globalCenterU, globalCenterV, argb, splatIndex, scale, 0.0, 0.0, writer);
	}

	public static void stampGlobal(int globalCenterU, int globalCenterV, int argb, int splatIndex, float scale,
			double tangentU, double tangentV, StagedCanvasWriter writer) {
		var random = java.util.concurrent.ThreadLocalRandom.current();

		double blobRadius = Math.max(1.2, (2.8 + random.nextDouble() * 1.6) * scale);
		double tanSpeedSq = tangentU * tangentU + tangentV * tangentV;
		boolean hasTangent = tanSpeedSq > 0.001;
		double elong;
		double angle;
		if (hasTangent) {
			angle = Math.atan2(tangentV, tangentU);
			double speed = Math.sqrt(tanSpeedSq);
			elong = 1.4 + Math.min(1.8, speed * 4.5) + random.nextDouble() * 0.4;
		} else {
			elong = 1.0 + random.nextDouble() * 0.5;
			angle = random.nextDouble() * Math.PI * 2.0;
		}

		int blobBound = (int) Math.ceil(blobRadius * elong * 1.5) + 2;
		double satRadius = Math.max(0.7, 3.0 * scale);
		int satBound = (int) Math.ceil(satRadius * 3.2) + 2;
		int half = Math.max(blobBound, satBound) + 2; // +2 guarantees 1-texel empty sentinel border
		int dim = half * 2 + 1;
		int cells = dim * dim;

		Scratch scratch = SCRATCH.get();
		scratch.ensureGrid(cells);
		byte[] grid = scratch.grid;
		byte[] layer = scratch.layer;
		int[] activeIdx = scratch.activeIdx;
		int[] rimBuf = scratch.rimBuf;

		int blobCount = fillOrganicBlob(grid, dim, half, blobRadius, elong, angle, hasTangent, random);

		// Roughen the main blob's rim lightly so it reads as pixel-art speckle rather than a smooth disc
		if (blobCount > 10) {
			int rimCount = 0;
			int minR = half - blobBound;
			int maxR = half + blobBound;
			for (int gy = minR; gy <= maxR; gy++) {
				int row = gy * dim;
				for (int gx = minR; gx <= maxR; gx++) {
					int idx = row + gx;
					if (grid[idx] != 0
						&& (grid[idx - 1] == 0 || grid[idx + 1] == 0 || grid[idx - dim] == 0 || grid[idx + dim] == 0)) {
						rimBuf[rimCount++] = idx;
					}
				}
			}
			int numToRemove = Math.min((int) (rimCount * 0.15f), blobCount - 3);
			for (int i = 0; i < numToRemove; i++) {
				int pick = i + random.nextInt(rimCount - i);
				int chosen = rimBuf[pick];
				rimBuf[pick] = rimBuf[i];
				grid[chosen] = 0;
			}
		}

		addSatellites(grid, dim, half, scale, satRadius, hasTangent, hasTangent ? Math.atan2(tangentV, tangentU) : 0.0, random);
		grid[half * dim + half] = 1;

		// Collect all active texel indices once
		int activeCount = 0;
		for (int gy = 1; gy < dim - 1; gy++) {
			int row = gy * dim;
			for (int gx = 1; gx < dim - 1; gx++) {
				int idx = row + gx;
				if (grid[idx] != 0) {
					activeIdx[activeCount++] = idx;
				}
			}
		}

		int rgb = argb & 0xFFFFFF;
		int baseAlpha = (argb >>> 24);
		if (baseAlpha == 0) baseAlpha = 255;
		boolean enableTranslucent = com.drinfonty.redfx.config.RedfxConfig.get().translucentEdges;

		// Pass 1: mark d1 (layer=1) for outermost perimeter pixels
		for (int i = 0; i < activeCount; i++) {
			int idx = activeIdx[i];
			if (grid[idx - 1] == 0 || grid[idx + 1] == 0 || grid[idx - dim] == 0 || grid[idx + dim] == 0) {
				layer[idx] = 1;
			} else {
				layer[idx] = 0;
			}
		}

		// Pass 2: mark d2 (layer=2) vs d3 (layer=3)
		boolean hasD2 = false;
		boolean hasD3 = false;
		for (int i = 0; i < activeCount; i++) {
			int idx = activeIdx[i];
			if (layer[idx] == 1) continue;
			if (layer[idx - 1] == 1 || layer[idx + 1] == 1 || layer[idx - dim] == 1 || layer[idx + dim] == 1) {
				layer[idx] = 2;
				hasD2 = true;
			} else {
				layer[idx] = 3;
				hasD3 = true;
			}
		}

		boolean hasInterior = hasD3 || hasD2;
		int minDistSq = Integer.MAX_VALUE;
		int maxDistSq = Integer.MIN_VALUE;
		if (!hasInterior) {
			for (int i = 0; i < activeCount; i++) {
				int idx = activeIdx[i];
				int du = (idx % dim) - half;
				int dv = (idx / dim) - half;
				int d2c = du * du + dv * dv;
				if (d2c < minDistSq) minDistSq = d2c;
				if (d2c > maxDistSq) maxDistSq = d2c;
			}
		}

		int centerIdx = half * dim + half;
		for (int i = 0; i < activeCount; i++) {
			int idx = activeIdx[i];
			int du = (idx % dim) - half;
			int dv = (idx / dim) - half;
			int gu = globalCenterU + du;
			int gv = globalCenterV + dv;
			byte lyr = layer[idx];

			int stage;
			if (hasD3) {
				stage = lyr == 3 ? 0 : (lyr == 2 ? 1 : 2);
			} else if (hasD2) {
				stage = lyr == 2 ? 0 : (idx == centerIdx ? 1 : 2);
			} else {
				int d2c = du * du + dv * dv;
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
				alpha = baseAlpha;
			} else if (stage == 1) {
				alpha = (int) Math.round(baseAlpha * (195.0 / 255.0));
			} else {
				int cardinalNeighbors = (grid[idx - 1] != 0 ? 1 : 0)
					+ (grid[idx + 1] != 0 ? 1 : 0)
					+ (grid[idx - dim] != 0 ? 1 : 0)
					+ (grid[idx + dim] != 0 ? 1 : 0);

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

			int col = (alpha << 24) | shade(rgb, gu, gv);
			writer.setTexel(gu, gv, col, stage);
		}
	}

	private static int fillOrganicBlob(byte[] grid, int dim, int half, double radius, double elong,
			double angle, boolean hasTangent, Random random) {
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);

		double amp0 = (0.10 + random.nextDouble() * 0.14);
		double amp1 = (0.10 + random.nextDouble() * 0.14) * 0.5;
		double amp2 = (0.10 + random.nextDouble() * 0.14) / 3.0;
		double phase0 = random.nextDouble() * Math.PI * 2.0;
		double phase1 = random.nextDouble() * Math.PI * 2.0;
		double phase2 = random.nextDouble() * Math.PI * 2.0;

		double shiftU = hasTangent ? cos * radius * 0.4 : 0.0;
		double shiftV = hasTangent ? sin * radius * 0.4 : 0.0;

		int bound = (int) Math.ceil(radius * elong * 1.5) + 2;
		int count = 0;
		for (int dv = -bound; dv <= bound; dv++) {
			int gy = half + dv;
			if (gy <= 0 || gy >= dim - 1) continue;
			int row = gy * dim;
			double ry = dv - shiftV;
			for (int du = -bound; du <= bound; du++) {
				int gx = half + du;
				if (gx <= 0 || gx >= dim - 1) continue;
				double rx = du - shiftU;
				double x = (rx * cos + ry * sin) / elong;
				double y = -rx * sin + ry * cos;
				double r = Math.sqrt(x * x + y * y);
				double theta = Math.atan2(y, x);
				double edge = 1.0
					+ amp0 * Math.sin(2.0 * theta + phase0)
					+ amp1 * Math.sin(3.0 * theta + phase1)
					+ amp2 * Math.sin(4.0 * theta + phase2);
				double jitter = (random.nextDouble() - 0.5) * 0.9;
				if (r + jitter <= radius * edge) {
					grid[row + gx] = 1;
					count++;
				}
			}
		}
		return count;
	}

	private static void addSatellites(byte[] grid, int dim, int half, float scale, double radius,
			boolean hasTangent, double impactAngle, Random random) {
		int count = (int) Math.round((1 + random.nextDouble() * 4) * Math.min(1.5, scale + 0.3));

		for (int i = 0; i < count; i++) {
			double a;
			double dist;
			if (hasTangent && random.nextDouble() < 0.65) {
				a = impactAngle + (random.nextDouble() - 0.5) * (Math.PI * 0.5);
				dist = radius * (1.4 + random.nextDouble() * 1.8);
			} else {
				a = random.nextDouble() * Math.PI * 2.0;
				dist = radius * (1.2 + random.nextDouble() * 1.0);
			}

			double cosA = Math.cos(a);
			double sinA = Math.sin(a);
			int su = half + (int) Math.round(cosA * dist);
			int sv = half + (int) Math.round(sinA * dist);
			int size = random.nextDouble() < 0.35 * Math.min(1.0, scale) ? 1 : 0;
			for (int dv = -size; dv <= size; dv++) {
				int gy = sv + dv;
				if (gy <= 0 || gy >= dim - 1) continue;
				for (int du = -size; du <= size; du++) {
					int gx = su + du;
					if (gx <= 0 || gx >= dim - 1) continue;
					if (size == 0 || Math.abs(du) + Math.abs(dv) <= size) {
						grid[gy * dim + gx] = 1;
					}
				}
			}
			if (random.nextDouble() < (hasTangent ? 0.6 : 0.4)) {
				int steps = (int) Math.ceil(dist);
				for (int s = 1; s < steps; s++) {
					double t = s / (double) steps;
					int gx = half + (int) Math.round(cosA * dist * t);
					int gy = half + (int) Math.round(sinA * dist * t);
					if (gx > 0 && gx < dim - 1 && gy > 0 && gy < dim - 1) {
						grid[gy * dim + gx] = 1;
					}
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
		Scratch scratch = SCRATCH.get();
		int[] edgeIndices = scratch.erodeEdge;
		int edgeCount = 0;
		int[] allPainted = scratch.erodeAll;
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
