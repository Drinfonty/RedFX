package com.drinfonty.redfx.client.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.drinfonty.redfx.canvas.Canvas;
import com.drinfonty.redfx.canvas.FaceAxes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AzaleaBlock;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.BigDripleafStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.HangingRootsBlock;
import com.drinfonty.redfx.client.ClientCanvasStore;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SporeBlossomBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Computes where decals sit on a block's surface based on its generic hitbox geometry
 * (slabs, snow, carpets, stairs, chests, paths, enchanting tables, etc.)
 * and whether a block is see-through (such as glass).
 */
public final class PaintSurface {
	public static final double NONE = -1.0;

	public record SurfaceCanvas(Canvas canvas, float facePlane, float surfaceY) {
		public SurfaceCanvas {
			if (canvas != null && (canvas.fadeStartTimeMs() != 0L || canvas.nextErodeTimeMs() != 0L)) {
				canvas = new Canvas(canvas.texels(), 0L, 0L);
			}
		}

		public SurfaceCanvas(Canvas canvas, float surfaceY) {
			this(canvas, Float.NaN, surfaceY);
		}
	}

	public record FacePlaneInfo(float facePlane, float surfaceY) {
	}

	public record BlockEdge(int edgeU, int edgeV, double colTop, double colBottom, double colPlane, boolean isInternal) {
		public BlockEdge(int edgeU, int edgeV, double colTop, double colBottom, double colPlane) {
			this(edgeU, edgeV, colTop, colBottom, colPlane, false);
		}
	}

	private PaintSurface() {
	}

	public static boolean isIgnored(BlockState state) {
		if (state == null || state.isAir()) {
			return true;
		}
		Block block = state.getBlock();
		return block instanceof BushBlock
			|| block instanceof AzaleaBlock
			|| block instanceof VineBlock
			|| block instanceof SugarCaneBlock
			|| block instanceof BambooStalkBlock
			|| block instanceof BambooSaplingBlock
			|| block instanceof GrowingPlantBlock
			|| block instanceof HangingRootsBlock
			|| block instanceof BigDripleafBlock
			|| block instanceof BigDripleafStemBlock
			|| block instanceof SporeBlossomBlock
			|| block instanceof CactusBlock
			|| isVegetation(block);
	}

	private static boolean isVegetation(Block block) {
		for (Class<?> c = block.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
			if ("VegetationBlock".equals(c.getSimpleName())) {
				return true;
			}
		}
		return false;
	}

	public static double topOf(BlockGetter level, BlockPos pos, BlockState state) {
		if (state == null || state.isAir() || isIgnored(state)) {
			return NONE;
		}

		BlockGetter bg = level != null ? level : EmptyBlockGetter.INSTANCE;
		BlockPos bp = pos != null ? pos : BlockPos.ZERO;

		if (state.isCollisionShapeFullBlock(bg, bp)) {
			return 1.0;
		}

		VoxelShape shape = state.getShape(bg, bp);
		if (shape.isEmpty()) {
			return NONE;
		}

		AABB bounds = shape.bounds();
		if (bounds.minX < 0.0 || bounds.minY < 0.0 || bounds.minZ < 0.0
			|| bounds.maxX > 1.0 || bounds.maxZ > 1.0 || bounds.maxY > 1.25) {
			return NONE;
		}

		VoxelShape collision = state.getCollisionShape(bg, bp);
		if (collision.isEmpty()) {
			// If it has no collision, it's only paintable if it's a flat ground cover (like snow layers).
			if (!(state.getBlock() instanceof SnowLayerBlock)) {
				return NONE;
			}
		}

		return bounds.maxY;
	}

	public static double surfaceElevationAt(BlockGetter level, BlockPos pos, BlockState state, double x, double z) {
		if (state == null || state.isAir()) {
			return NONE;
		}

		BlockGetter bg = level != null ? level : EmptyBlockGetter.INSTANCE;
		BlockPos bp = pos != null ? pos : BlockPos.ZERO;

		if (state.isCollisionShapeFullBlock(bg, bp)) {
			return 1.0;
		}

		if (topOf(bg, bp, state) == NONE) {
			return NONE;
		}

		VoxelShape shape = state.getShape(bg, bp);
		if (shape.isEmpty()) {
			return NONE;
		}

		double maxTop = NONE;
		for (AABB box : shape.toAabbs()) {
			if (x >= box.minX - 1e-4 && x <= box.maxX + 1e-4 && z >= box.minZ - 1e-4 && z <= box.maxZ + 1e-4) {
				if (box.maxY > maxTop) {
					maxTop = box.maxY;
				}
			}
		}

		return maxTop;
	}

	public static double surfaceBottomAt(BlockGetter level, BlockPos pos, BlockState state, double x, double z) {
		if (state == null || state.isAir()) {
			return 0.0;
		}

		BlockGetter bg = level != null ? level : EmptyBlockGetter.INSTANCE;
		BlockPos bp = pos != null ? pos : BlockPos.ZERO;

		if (state.isCollisionShapeFullBlock(bg, bp)) {
			return 0.0;
		}

		if (topOf(bg, bp, state) == NONE) {
			return 0.0;
		}

		VoxelShape shape = state.getShape(bg, bp);
		if (shape.isEmpty()) {
			return 0.0;
		}

		double minBottom = 1.0;
		boolean found = false;
		for (AABB box : shape.toAabbs()) {
			if (x >= box.minX - 1e-4 && x <= box.maxX + 1e-4 && z >= box.minZ - 1e-4 && z <= box.maxZ + 1e-4) {
				if (box.minY < minBottom) {
					minBottom = box.minY;
				}
				found = true;
			}
		}

		return found ? minBottom : 0.0;
	}

	public static double elevationAt(BlockGetter level, BlockPos pos, double x, double z) {
		BlockGetter bg = level != null ? level : EmptyBlockGetter.INSTANCE;
		BlockPos bp = pos != null ? pos : BlockPos.ZERO;

		BlockPos above = bp.above();
		BlockState aboveState = bg.getBlockState(above);
		if (!aboveState.isAir() && aboveState.getFluidState().isEmpty()) {
			double top = surfaceElevationAt(bg, above, aboveState, x, z);
			if (top != NONE && top < 1.0) {
				return above.getY() + top;
			}
		}

		BlockState state = bg.getBlockState(bp);
		if (!state.isAir() && state.getFluidState().isEmpty()) {
			double top = surfaceElevationAt(bg, bp, state, x, z);
			if (top != NONE) {
				return bp.getY() + top;
			}
		}

		BlockPos below = bp.below();
		BlockState belowState = bg.getBlockState(below);
		if (!belowState.isAir() && belowState.getFluidState().isEmpty()) {
			double top = surfaceElevationAt(bg, below, belowState, x, z);
			if (top != NONE) {
				return below.getY() + top;
			}
		}

		return NONE;
	}

	public static boolean isUpperStairStep(VoxelShape shape, double x, double z) {
		for (AABB aabb : shape.toAabbs()) {
			if (aabb.contains(x, 0.75, z)) {
				return true;
			}
		}
		return false;
	}

	public static double columnX(int face, int pu) {
		double u = (pu + 0.5) / (double) Canvas.SIZE;
		return switch (face) {
			case FaceAxes.NORTH -> 1.0 - u;
			case FaceAxes.SOUTH -> u;
			case FaceAxes.WEST -> 0.01;
			case FaceAxes.EAST -> 0.99;
			default -> u;
		};
	}

	public static double columnZ(int face, int pu) {
		double u = (pu + 0.5) / (double) Canvas.SIZE;
		return switch (face) {
			case FaceAxes.NORTH -> 0.01;
			case FaceAxes.SOUTH -> 0.99;
			case FaceAxes.WEST -> u;
			case FaceAxes.EAST -> 1.0 - u;
			default -> u;
		};
	}

	public static void findEdgesForDirection(BlockGetter level, BlockPos pos, BlockState state, Direction hDir,
			BlockEdge[] outOuter, BlockEdge[] outInternal) {
		if (state == null || state.isAir()) return;

		BlockGetter bg = level != null ? level : EmptyBlockGetter.INSTANCE;
		BlockPos bp = pos != null ? pos : BlockPos.ZERO;

		if (state.isCollisionShapeFullBlock(bg, bp)) {
			double colPlane = (hDir == Direction.NORTH || hDir == Direction.WEST) ? 0.0 : 1.0;
			boolean ns = hDir == Direction.NORTH || hDir == Direction.SOUTH;
			int fixed = (hDir == Direction.NORTH || hDir == Direction.WEST) ? 0 : 15;
			for (int coord = 0; coord < Canvas.SIZE; coord++) {
				int edgeU = ns ? coord : fixed;
				int edgeV = ns ? fixed : coord;
				outOuter[coord] = new BlockEdge(edgeU, edgeV, 1.0, 0.0, colPlane, false);
			}
			return;
		}

		VoxelShape shape = state.getShape(bg, bp);
		if (shape.isEmpty()) return;
		List<AABB> boxes = shape.toAabbs();
		List<BlockEdge> tmp = new ArrayList<>(2);

		for (int coord = 0; coord < Canvas.SIZE; coord++) {
			tmp.clear();
			collectEdgesForCoord(bg, bp, state, boxes, hDir, coord, tmp);
			for (int i = 0; i < tmp.size(); i++) {
				BlockEdge e = tmp.get(i);
				if (e.isInternal()) {
					if (outInternal != null && outInternal[coord] == null) outInternal[coord] = e;
				} else if (outOuter[coord] == null) {
					outOuter[coord] = e;
				}
			}
		}
	}

	public static List<BlockEdge> findEdges(BlockGetter level, BlockPos pos, BlockState state, Direction hDir, int coord) {
		if (state == null || state.isAir()) return List.of();

		BlockGetter bg = level != null ? level : EmptyBlockGetter.INSTANCE;
		BlockPos bp = pos != null ? pos : BlockPos.ZERO;

		if (state.isCollisionShapeFullBlock(bg, bp)) {
			int edgeU = (hDir == Direction.NORTH || hDir == Direction.SOUTH) ? coord : (hDir == Direction.WEST ? 0 : 15);
			int edgeV = (hDir == Direction.WEST || hDir == Direction.EAST) ? coord : (hDir == Direction.NORTH ? 0 : 15);
			double colPlane = (hDir == Direction.NORTH || hDir == Direction.WEST) ? 0.0 : 1.0;
			return List.of(new BlockEdge(edgeU, edgeV, 1.0, 0.0, colPlane, false));
		}

		VoxelShape shape = state.getShape(bg, bp);
		if (shape.isEmpty()) return List.of();

		List<AABB> boxes = shape.toAabbs();
		List<BlockEdge> edges = new ArrayList<>(2);
		collectEdgesForCoord(bg, bp, state, boxes, hDir, coord, edges);
		return edges;
	}

	private static boolean matchesTangent(AABB box, int face, double tangent) {
		return switch (face) {
			case FaceAxes.NORTH, FaceAxes.SOUTH -> tangent >= box.minX - 1e-4 && tangent <= box.maxX + 1e-4;
			case FaceAxes.WEST, FaceAxes.EAST -> tangent >= box.minZ - 1e-4 && tangent <= box.maxZ + 1e-4;
			default -> false;
		};
	}

	private static BlockEdge toBlockEdge(AABB box, Direction hDir, int coord, double colTop, double colBottom, boolean isInternal) {
		int edgeU;
		int edgeV;
		double colPlane;
		switch (hDir) {
			case NORTH -> {
				edgeU = coord;
				edgeV = clampTexel(box.minZ * 16.0);
				colPlane = box.minZ;
			}
			case SOUTH -> {
				edgeU = coord;
				edgeV = clampTexel(box.maxZ * 16.0 - 1e-4);
				colPlane = box.maxZ;
			}
			case WEST -> {
				edgeU = clampTexel(box.minX * 16.0);
				edgeV = coord;
				colPlane = box.minX;
			}
			case EAST -> {
				edgeU = clampTexel(box.maxX * 16.0 - 1e-4);
				edgeV = coord;
				colPlane = box.maxX;
			}
			default -> throw new IllegalArgumentException("invalid horizontal direction: " + hDir);
		}
		return new BlockEdge(edgeU, edgeV, colTop, colBottom, colPlane, isInternal);
	}

	private static void collectEdgesForCoord(BlockGetter bg, BlockPos bp, BlockState state, List<AABB> boxes,
			Direction hDir, int coord, List<BlockEdge> edges) {
		double tangent = (coord + 0.5) / (double) Canvas.SIZE;
		int face = hDir.get3DDataValue();

		double maxTop = NONE;
		for (int i = 0; i < boxes.size(); i++) {
			AABB box = boxes.get(i);
			if (matchesTangent(box, face, tangent) && box.maxY > maxTop) {
				maxTop = box.maxY;
			}
		}

		if (maxTop == NONE) return;

		boolean hasUpperStructure = maxTop > 0.35;
		AABB bestOuterBox = null;

		for (int i = 0; i < boxes.size(); i++) {
			AABB box = boxes.get(i);
			if (!matchesTangent(box, face, tangent)) continue;

			if (hasUpperStructure && box.maxY <= 0.25 + 1e-4) {
				continue;
			}

			// Check for internal vertical drop (e.g. stair riser, composter/cauldron/hopper cavity wall)
			double testOffset = 0.05;
			double testX = tangent;
			double testZ = tangent;
			boolean canTestInternal = false;

			switch (hDir) {
				case NORTH -> {
					testZ = box.minZ - testOffset;
					canTestInternal = testZ >= 0.0 && box.minZ > 0.05;
				}
				case SOUTH -> {
					testZ = box.maxZ + testOffset;
					canTestInternal = testZ <= 1.0 && box.maxZ < 0.95;
				}
				case WEST -> {
					testX = box.minX - testOffset;
					canTestInternal = testX >= 0.0 && box.minX > 0.05;
				}
				case EAST -> {
					testX = box.maxX + testOffset;
					canTestInternal = testX <= 1.0 && box.maxX < 0.95;
				}
			}

			if (canTestInternal) {
				double internalFloor = surfaceElevationAt(bg, bp, state, testX, testZ);
				if (internalFloor != NONE && internalFloor < box.maxY - 0.05) {
					edges.add(toBlockEdge(box, hDir, coord, box.maxY, internalFloor, true));
				}
			}

			if (isBetterOuterBox(box, bestOuterBox, face)) {
				bestOuterBox = box;
			}
		}

		if (bestOuterBox != null) {
			edges.add(toBlockEdge(bestOuterBox, hDir, coord, bestOuterBox.maxY, bestOuterBox.minY, false));
		}
	}

	public static BlockEdge findEdge(BlockGetter level, BlockPos pos, BlockState state, Direction hDir, int coord) {
		List<BlockEdge> edges = findEdges(level, pos, state, hDir, coord);
		if (edges.isEmpty()) return null;
		for (BlockEdge e : edges) {
			if (!e.isInternal()) return e;
		}
		return edges.get(0);
	}

	public static List<SurfaceCanvas> splitCanvas(BlockGetter level, BlockPos pos, BlockState state, int face, Canvas canvas) {
		Canvas topCanvas = null;
		try {
			ClientCanvasStore store = ClientCanvasStore.get();
			if (store != null && pos != null) {
				topCanvas = store.get(pos, FaceAxes.UP);
			}
		} catch (Throwable ignored) {
		}
		return splitCanvas(level, pos, state, face, canvas, topCanvas);
	}

	public static List<SurfaceCanvas> splitCanvas(BlockGetter level, BlockPos pos, BlockState state, int face, Canvas canvas, Canvas topCanvas) {
		if (state == null || state.isAir() || isIgnored(state)) {
			return List.of();
		}

		BlockGetter bg = level != null ? level : EmptyBlockGetter.INSTANCE;
		BlockPos bp = pos != null ? pos : BlockPos.ZERO;

		if (state.isCollisionShapeFullBlock(bg, bp)) {
			float surfaceY = (float) planeFor(level, pos, state, face);
			float facePlane = PaintGeometry.defaultFacePlane(face, surfaceY);
			return List.of(new SurfaceCanvas(canvas, facePlane, surfaceY));
		}

		int[] texels = canvas.texels();
		Map<FacePlaneInfo, int[]> planes = new LinkedHashMap<>(4);

		if (face == FaceAxes.UP) {
			if (topOf(bg, bp, state) == NONE) {
				return List.of();
			}
			VoxelShape shape = state.getShape(bg, bp);
			if (shape.isEmpty()) {
				return List.of();
			}
			List<AABB> boxes = shape.toAabbs();
			for (int pv = 0; pv < Canvas.SIZE; pv++) {
				double z = (pv + 0.5) / (double) Canvas.SIZE;
				int row = pv * Canvas.SIZE;
				for (int pu = 0; pu < Canvas.SIZE; pu++) {
					int idx = row + pu;
					int col = texels[idx];
					if (col == 0) {
						continue;
					}

					double x = (pu + 0.5) / (double) Canvas.SIZE;
					double top = NONE;
					for (int b = 0; b < boxes.size(); b++) {
						AABB box = boxes.get(b);
						if (x >= box.minX - 1e-4 && x <= box.maxX + 1e-4 && z >= box.minZ - 1e-4 && z <= box.maxZ + 1e-4) {
							if (box.maxY > top) {
								top = box.maxY;
							}
						}
					}
					if (top == NONE) {
						continue;
					}

					float surfaceY = (float) (Math.round(top * 10000.0) / 10000.0);
					FacePlaneInfo key = new FacePlaneInfo(surfaceY, surfaceY);
					planes.computeIfAbsent(key, k -> new int[Canvas.TEXELS])[idx] = col;
				}
			}
		} else if (face == FaceAxes.DOWN) {
			boolean hasTop = topOf(bg, bp, state) != NONE;
			VoxelShape shape = hasTop ? state.getShape(bg, bp) : null;
			List<AABB> boxes = (shape != null && !shape.isEmpty()) ? shape.toAabbs() : List.of();
			for (int pv = 0; pv < Canvas.SIZE; pv++) {
				double z = 1.0 - (pv + 0.5) / (double) Canvas.SIZE;
				int row = pv * Canvas.SIZE;
				for (int pu = 0; pu < Canvas.SIZE; pu++) {
					int idx = row + pu;
					int col = texels[idx];
					if (col == 0) {
						continue;
					}

					double x = (pu + 0.5) / (double) Canvas.SIZE;
					double bottom = 0.0;
					if (!boxes.isEmpty()) {
						double minBottom = 1.0;
						boolean found = false;
						for (int b = 0; b < boxes.size(); b++) {
							AABB box = boxes.get(b);
							if (x >= box.minX - 1e-4 && x <= box.maxX + 1e-4 && z >= box.minZ - 1e-4 && z <= box.maxZ + 1e-4) {
								if (box.minY < minBottom) {
									minBottom = box.minY;
								}
								found = true;
							}
						}
						bottom = found ? minBottom : 0.0;
					}
					float surfaceY = (float) (Math.round(bottom * 10000.0) / 10000.0);
					FacePlaneInfo key = new FacePlaneInfo(surfaceY, surfaceY);
					planes.computeIfAbsent(key, k -> new int[Canvas.TEXELS])[idx] = col;
				}
			}
		} else {
			// Horizontal faces: NORTH, SOUTH, WEST, EAST
			// Column geometry depends only on pu, so compute candidate/chosen boxes once per column instead of per texel
			VoxelShape shape = state.getShape(bg, bp);
			List<AABB> boxes = shape.toAabbs();
			int[] topT = topCanvas != null ? topCanvas.texels() : null;
			List<AABB> candidateBoxes = new ArrayList<>(boxes.size());
			List<AABB> chosenBoxes = new ArrayList<>(2);

			for (int pu = 0; pu < Canvas.SIZE; pu++) {
				boolean colHasPainted = false;
				for (int pv = 0; pv < Canvas.SIZE; pv++) {
					if (texels[pv * Canvas.SIZE + pu] != 0) {
						colHasPainted = true;
						break;
					}
				}
				if (!colHasPainted) continue;

				double u = (pu + 0.5) / (double) Canvas.SIZE;
				double tangent = switch (face) {
					case FaceAxes.NORTH -> 1.0 - u;
					case FaceAxes.SOUTH -> u;
					case FaceAxes.WEST -> u;
					case FaceAxes.EAST -> 1.0 - u;
					default -> u;
				};

				double maxTop = NONE;
				for (int b = 0; b < boxes.size(); b++) {
					AABB box = boxes.get(b);
					if (matchesTangent(box, face, tangent) && box.maxY > maxTop) {
						maxTop = box.maxY;
					}
				}
				boolean hasUpperStructure = maxTop > 0.35;

				boolean hasInternalBlood = false;
				boolean hasOuterBlood = false;
				if (topT != null) {
					switch (face) {
						case FaceAxes.SOUTH -> {
							for (int v = 0; v <= 7; v++) {
								if (topT[v * Canvas.SIZE + pu] != 0) { hasInternalBlood = true; break; }
							}
							for (int v = 8; v < Canvas.SIZE; v++) {
								if (topT[v * Canvas.SIZE + pu] != 0) { hasOuterBlood = true; break; }
							}
						}
						case FaceAxes.NORTH -> {
							int uCoord = 15 - pu;
							for (int v = 8; v < Canvas.SIZE; v++) {
								if (topT[v * Canvas.SIZE + uCoord] != 0) { hasInternalBlood = true; break; }
							}
							for (int v = 0; v <= 7; v++) {
								if (topT[v * Canvas.SIZE + uCoord] != 0) { hasOuterBlood = true; break; }
							}
						}
						case FaceAxes.WEST -> {
							int rowBase = pu * Canvas.SIZE;
							for (int un = 8; un < Canvas.SIZE; un++) {
								if (topT[rowBase + un] != 0) { hasInternalBlood = true; break; }
							}
							for (int un = 0; un <= 7; un++) {
								if (topT[rowBase + un] != 0) { hasOuterBlood = true; break; }
							}
						}
						case FaceAxes.EAST -> {
							int rowBase = (15 - pu) * Canvas.SIZE;
							for (int un = 0; un <= 7; un++) {
								if (topT[rowBase + un] != 0) { hasInternalBlood = true; break; }
							}
							for (int un = 8; un < Canvas.SIZE; un++) {
								if (topT[rowBase + un] != 0) { hasOuterBlood = true; break; }
							}
						}
					}
				}

				candidateBoxes.clear();
				for (int b = 0; b < boxes.size(); b++) {
					AABB box = boxes.get(b);
					if (!matchesTangent(box, face, tangent)) continue;
					if (hasUpperStructure && box.maxY <= 0.25 + 1e-4) continue;
					candidateBoxes.add(box);
				}
				if (candidateBoxes.isEmpty()) continue;

				chosenBoxes.clear();
				if (hasInternalBlood && !hasOuterBlood) {
					for (int b = 0; b < candidateBoxes.size(); b++) {
						AABB box = candidateBoxes.get(b);
						if (isInternalBox(box, face)) chosenBoxes.add(box);
					}
				} else if (hasOuterBlood && !hasInternalBlood) {
					for (int b = 0; b < candidateBoxes.size(); b++) {
						AABB box = candidateBoxes.get(b);
						if (!isInternalBox(box, face)) chosenBoxes.add(box);
					}
				} else if (hasInternalBlood && hasOuterBlood) {
					chosenBoxes.addAll(candidateBoxes);
				}

				if (chosenBoxes.isEmpty()) {
					AABB bestBox = null;
					for (int b = 0; b < candidateBoxes.size(); b++) {
						AABB box = candidateBoxes.get(b);
						boolean outerHalf = switch (face) {
							case FaceAxes.NORTH -> box.minZ < 0.5 - 1e-4;
							case FaceAxes.SOUTH -> box.maxZ > 0.5 + 1e-4;
							case FaceAxes.WEST -> box.minX < 0.5 - 1e-4;
							case FaceAxes.EAST -> box.maxX > 0.5 + 1e-4;
							default -> true;
						};
						if (!outerHalf) continue;
						if (isBetterOuterBox(box, bestBox, face)) {
							bestBox = box;
						}
					}
					if (bestBox == null) {
						for (int b = 0; b < candidateBoxes.size(); b++) {
							AABB box = candidateBoxes.get(b);
							if (isBetterOuterBox(box, bestBox, face)) {
								bestBox = box;
							}
						}
					}
					if (bestBox != null) {
						chosenBoxes.add(bestBox);
					}
				}

				for (int c = 0; c < chosenBoxes.size(); c++) {
					AABB targetBox = chosenBoxes.get(c);
					double plane = boxPlaneForFace(targetBox, face);
					double top = targetBox.maxY;
					float surfaceY = (float) (Math.round(top * 10000.0) / 10000.0);
					float facePlane = (float) (Math.round(plane * 10000.0) / 10000.0);
					FacePlaneInfo key = new FacePlaneInfo(facePlane, surfaceY);

					for (int pv = 0; pv < Canvas.SIZE; pv++) {
						int idx = pv * Canvas.SIZE + pu;
						int col = texels[idx];
						if (col == 0) continue;

						double pixelY = top - (pv + 0.5) / (double) Canvas.SIZE;
						boolean onFace = false;
						for (int b = 0; b < boxes.size(); b++) {
							AABB box = boxes.get(b);
							if (!matchesTangent(box, face, tangent)) continue;
							if (Math.abs(boxPlaneForFace(box, face) - plane) < 1e-4
								&& pixelY >= box.minY - 0.03 && pixelY <= box.maxY + 0.03) {
								onFace = true;
								break;
							}
						}
						if (!onFace) continue;

						planes.computeIfAbsent(key, k -> new int[Canvas.TEXELS])[idx] = col;
					}
				}
			}
		}

		if (planes.isEmpty()) {
			return List.of();
		}

		List<Map.Entry<FacePlaneInfo, int[]>> sorted = new ArrayList<>(planes.entrySet());
		sorted.sort((a, b) -> Float.compare(b.getKey().surfaceY(), a.getKey().surfaceY()));

		List<SurfaceCanvas> result = new ArrayList<>(sorted.size());
		for (Map.Entry<FacePlaneInfo, int[]> entry : sorted) {
			result.add(new SurfaceCanvas(
				new Canvas(entry.getValue(), canvas.fadeStartTimeMs(), canvas.nextErodeTimeMs()),
				entry.getKey().facePlane(),
				entry.getKey().surfaceY()
			));
		}

		return result;
	}

	public static boolean isSeeThrough(BlockState state) {
		return !state.canOcclude();
	}

	public static double planeFor(BlockGetter level, BlockPos pos, BlockState state, int face) {
		if (face == FaceAxes.DOWN) {
			return 1.0;
		}
		double top = topOf(level, pos, state);
		return top == NONE ? 1.0 : top;
	}

	public static float planeFor(BlockGetter level, BlockPos pos, BlockState state, Direction face) {
		return (float) planeFor(level, pos, state, face.get3DDataValue());
	}

	private static double boxPlaneForFace(AABB box, int face) {
		return switch (face) {
			case FaceAxes.NORTH -> box.minZ;
			case FaceAxes.SOUTH -> box.maxZ;
			case FaceAxes.WEST -> box.minX;
			case FaceAxes.EAST -> box.maxX;
			default -> 0.0;
		};
	}

	private static boolean isBetterOuterBox(AABB box, AABB bestBox, int face) {
		if (bestBox == null) {
			return true;
		}
		double boxPlane = boxPlaneForFace(box, face);
		double bestPlane = boxPlaneForFace(bestBox, face);
		if (face == FaceAxes.NORTH || face == FaceAxes.WEST) {
			if (boxPlane < bestPlane - 1e-4) {
				return true;
			}
			return Math.abs(boxPlane - bestPlane) <= 1e-4 && box.maxY > bestBox.maxY + 1e-4;
		} else {
			if (boxPlane > bestPlane + 1e-4) {
				return true;
			}
			return Math.abs(boxPlane - bestPlane) <= 1e-4 && box.maxY > bestBox.maxY + 1e-4;
		}
	}

	private static boolean isInternalBox(AABB box, int face) {
		return switch (face) {
			case FaceAxes.NORTH -> box.minZ > 0.05;
			case FaceAxes.SOUTH -> box.maxZ < 0.95;
			case FaceAxes.WEST -> box.minX > 0.05;
			case FaceAxes.EAST -> box.maxX < 0.95;
			default -> false;
		};
	}

	private static int clampTexel(double value) {
		return Math.max(0, Math.min(15, (int) Math.floor(value)));
	}
}
