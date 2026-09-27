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

	public record SurfaceCanvas(Canvas canvas, float surfaceY) {
	}

	private PaintSurface() {
	}

	public static double topOf(BlockGetter level, BlockPos pos, BlockState state) {
		if (state == null || state.isAir()) {
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
			// Plants, flowers, and torches have narrow horizontal bounds (< 0.8).
			if (bounds.maxX - bounds.minX < 0.8 || bounds.maxZ - bounds.minZ < 0.8) {
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

	public static List<SurfaceCanvas> splitCanvas(BlockGetter level, BlockPos pos, BlockState state, int face, Canvas canvas) {
		if (state == null || state.isAir()) {
			float surfaceY = (float) planeFor(level, pos, state, face);
			return List.of(new SurfaceCanvas(canvas, surfaceY));
		}

		BlockGetter bg = level != null ? level : EmptyBlockGetter.INSTANCE;
		BlockPos bp = pos != null ? pos : BlockPos.ZERO;

		if (state.isCollisionShapeFullBlock(bg, bp) || face == FaceAxes.DOWN) {
			float surfaceY = (float) planeFor(level, pos, state, face);
			return List.of(new SurfaceCanvas(canvas, surfaceY));
		}

		int[] texels = canvas.texels();
		Map<Float, int[]> planes = new LinkedHashMap<>(4);

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
					planes.computeIfAbsent(surfaceY, k -> new int[Canvas.TEXELS])[idx] = col;
				}
			}
		} else {
			// Horizontal faces: NORTH, SOUTH, WEST, EAST
			for (int pv = 0; pv < Canvas.SIZE; pv++) {
				for (int pu = 0; pu < Canvas.SIZE; pu++) {
					int idx = pv * Canvas.SIZE + pu;
					int col = texels[idx];
					if (col == 0) {
						continue;
					}

					double x = columnX(face, pu);
					double z = columnZ(face, pu);
					double top = surfaceElevationAt(bg, bp, state, x, z);
					if (top == NONE) {
						continue;
					}

					float surfaceY = (float) (Math.round(top * 10000.0) / 10000.0);
					planes.computeIfAbsent(surfaceY, k -> new int[Canvas.TEXELS])[idx] = col;
				}
			}
		}

		if (planes.isEmpty()) {
			float surfaceY = (float) planeFor(level, pos, state, face);
			return List.of(new SurfaceCanvas(canvas, surfaceY));
		}

		List<Map.Entry<Float, int[]>> sorted = new ArrayList<>(planes.entrySet());
		sorted.sort((a, b) -> Float.compare(b.getKey(), a.getKey()));

		List<SurfaceCanvas> result = new ArrayList<>(sorted.size());
		for (Map.Entry<Float, int[]> entry : sorted) {
			result.add(new SurfaceCanvas(new Canvas(entry.getValue(), canvas.fadeStartTimeMs(), canvas.nextErodeTimeMs()), entry.getKey()));
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
