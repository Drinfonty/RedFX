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
import net.minecraft.world.level.block.LeavesBlock;
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
		public SurfaceCanvas(Canvas canvas, float surfaceY) {
			this(canvas, Float.NaN, surfaceY);
		}
	}

	public record FacePlaneInfo(float facePlane, float surfaceY) {
	}

	public record BlockEdge(int edgeU, int edgeV, double colTop, double colBottom, double colPlane) {
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
			|| block instanceof LeavesBlock
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
			|| bounds.maxX > 1.0 || bounds.maxZ > 1.0 || bounds.maxY > 1.0) {
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

	public static BlockEdge findEdge(BlockGetter level, BlockPos pos, BlockState state, Direction hDir, int coord) {
		if (state == null || state.isAir()) return null;

		BlockGetter bg = level != null ? level : EmptyBlockGetter.INSTANCE;
		BlockPos bp = pos != null ? pos : BlockPos.ZERO;

		VoxelShape shape = state.getShape(bg, bp);
		if (shape.isEmpty()) return null;

		double tangent = (coord + 0.5) / (double) Canvas.SIZE;

		AABB bestBox = null;
		double bestTop = NONE;

		for (AABB box : shape.toAabbs()) {
			boolean tangentMatch = switch (hDir) {
				case NORTH, SOUTH -> tangent >= box.minX - 1e-4 && tangent <= box.maxX + 1e-4;
				case WEST, EAST -> tangent >= box.minZ - 1e-4 && tangent <= box.maxZ + 1e-4;
				default -> false;
			};
			if (!tangentMatch) continue;

			if (box.maxY > bestTop) {
				bestTop = box.maxY;
				bestBox = box;
			}
		}

		if (bestBox == null) return null;

		int edgeU;
		int edgeV;
		double colPlane;

		switch (hDir) {
			case NORTH -> {
				edgeU = coord;
				edgeV = Math.clamp((int) Math.floor(bestBox.minZ * 16.0), 0, 15);
				colPlane = bestBox.minZ;
			}
			case SOUTH -> {
				edgeU = coord;
				edgeV = Math.clamp((int) Math.floor(bestBox.maxZ * 16.0 - 1e-4), 0, 15);
				colPlane = bestBox.maxZ;
			}
			case WEST -> {
				edgeU = Math.clamp((int) Math.floor(bestBox.minX * 16.0), 0, 15);
				edgeV = coord;
				colPlane = bestBox.minX;
			}
			case EAST -> {
				edgeU = Math.clamp((int) Math.floor(bestBox.maxX * 16.0 - 1e-4), 0, 15);
				edgeV = coord;
				colPlane = bestBox.maxX;
			}
			default -> throw new IllegalArgumentException("invalid horizontal direction: " + hDir);
		}

		return new BlockEdge(edgeU, edgeV, bestBox.maxY, bestBox.minY, colPlane);
	}

	public static List<SurfaceCanvas> splitCanvas(BlockGetter level, BlockPos pos, BlockState state, int face, Canvas canvas) {
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
			for (int pv = 0; pv < Canvas.SIZE; pv++) {
				double z = (pv + 0.5) / (double) Canvas.SIZE;
				for (int pu = 0; pu < Canvas.SIZE; pu++) {
					int idx = pv * Canvas.SIZE + pu;
					int col = texels[idx];
					if (col == 0) {
						continue;
					}

					double x = (pu + 0.5) / (double) Canvas.SIZE;
					double top = surfaceElevationAt(bg, bp, state, x, z);
					if (top == NONE) {
						continue;
					}

					float surfaceY = (float) (Math.round(top * 10000.0) / 10000.0);
					FacePlaneInfo key = new FacePlaneInfo(surfaceY, surfaceY);
					planes.computeIfAbsent(key, k -> new int[Canvas.TEXELS])[idx] = col;
				}
			}
		} else if (face == FaceAxes.DOWN) {
			for (int pv = 0; pv < Canvas.SIZE; pv++) {
				double z = 1.0 - (pv + 0.5) / (double) Canvas.SIZE;
				for (int pu = 0; pu < Canvas.SIZE; pu++) {
					int idx = pv * Canvas.SIZE + pu;
					int col = texels[idx];
					if (col == 0) {
						continue;
					}

					double x = (pu + 0.5) / (double) Canvas.SIZE;
					double bottom = surfaceBottomAt(bg, bp, state, x, z);
					float surfaceY = (float) (Math.round(bottom * 10000.0) / 10000.0);
					FacePlaneInfo key = new FacePlaneInfo(surfaceY, surfaceY);
					planes.computeIfAbsent(key, k -> new int[Canvas.TEXELS])[idx] = col;
				}
			}
		} else {
			// Horizontal faces: NORTH, SOUTH, WEST, EAST
			VoxelShape shape = state.getShape(bg, bp);
			AABB bounds = shape.bounds();

			for (int pv = 0; pv < Canvas.SIZE; pv++) {
				for (int pu = 0; pu < Canvas.SIZE; pu++) {
					int idx = pv * Canvas.SIZE + pu;
					int col = texels[idx];
					if (col == 0) {
						continue;
					}

					double u = (pu + 0.5) / (double) Canvas.SIZE;
					double x = switch (face) {
						case FaceAxes.NORTH -> 1.0 - u;
						case FaceAxes.SOUTH -> u;
						case FaceAxes.WEST -> bounds.minX + 0.01;
						case FaceAxes.EAST -> bounds.maxX - 0.01;
						default -> u;
					};
					double z = switch (face) {
						case FaceAxes.NORTH -> bounds.minZ + 0.01;
						case FaceAxes.SOUTH -> bounds.maxZ - 0.01;
						case FaceAxes.WEST -> u;
						case FaceAxes.EAST -> 1.0 - u;
						default -> u;
					};

					double top = surfaceElevationAt(bg, bp, state, x, z);
					if (top == NONE) {
						continue;
					}

					float surfaceY = (float) (Math.round(top * 10000.0) / 10000.0);
					double plane = switch (face) {
						case FaceAxes.NORTH -> bounds.minZ;
						case FaceAxes.SOUTH -> bounds.maxZ;
						case FaceAxes.WEST -> bounds.minX;
						case FaceAxes.EAST -> bounds.maxX;
						default -> 0.0;
					};
					float facePlane = (float) (Math.round(plane * 10000.0) / 10000.0);
					FacePlaneInfo key = new FacePlaneInfo(facePlane, surfaceY);
					planes.computeIfAbsent(key, k -> new int[Canvas.TEXELS])[idx] = col;
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
}
