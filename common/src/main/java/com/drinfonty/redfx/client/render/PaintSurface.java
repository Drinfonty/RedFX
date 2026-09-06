package com.drinfonty.redfx.client.render;

import java.util.ArrayList;
import java.util.List;

import com.drinfonty.redfx.canvas.Canvas;
import com.drinfonty.redfx.canvas.FaceAxes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Computes where decals sit on a block's top face (slabs, snow, carpets, stairs, etc.)
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

		VoxelShape shape = state.getShape(bg, bp);
		if (shape.isEmpty()) {
			return NONE;
		}

		AABB bounds = shape.bounds();
		if (bounds.minX < 0.0 || bounds.minY < 0.0 || bounds.minZ < 0.0
			|| bounds.maxX > 1.0 || bounds.maxZ > 1.0 || bounds.maxY > 1.0) {
			return NONE;
		}

		if (state.getBlock() instanceof AbstractChestBlock) {
			if (Shapes.joinIsNotEmpty(shape, Shapes.box(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ), BooleanOp.NOT_SAME)) {
				return NONE;
			}
			return bounds.maxY;
		}

		if (state.getBlock() instanceof StairBlock) {
			return bounds.maxY;
		}

		// Non-chest, non-stair surfaces must cover the full horizontal cross-section of the block (full blocks, slabs, snow, carpets)
		if (bounds.minX > 0.0 || bounds.minZ > 0.0 || bounds.maxX < 1.0 || bounds.maxZ < 1.0) {
			return NONE;
		}

		if (Shapes.joinIsNotEmpty(shape, Shapes.box(0.0, bounds.minY, 0.0, 1.0, bounds.maxY, 1.0), BooleanOp.NOT_SAME)) {
			return NONE;
		}

		return bounds.maxY;
	}

	public static boolean isUpperStairStep(VoxelShape shape, double x, double z) {
		for (AABB aabb : shape.toAabbs()) {
			if (aabb.contains(x, 0.75, z)) {
				return true;
			}
		}
		return false;
	}

	public static List<SurfaceCanvas> splitCanvas(BlockGetter level, BlockPos pos, BlockState state, int face, Canvas canvas) {
		if (face == FaceAxes.UP && state != null && state.getBlock() instanceof StairBlock && state.getValue(StairBlock.HALF) == Half.BOTTOM) {
			BlockGetter bg = level != null ? level : EmptyBlockGetter.INSTANCE;
			BlockPos bp = pos != null ? pos : BlockPos.ZERO;
			VoxelShape shape = state.getShape(bg, bp);
			int[] texels = canvas.texels();
			int[] upper = null;
			int[] lower = null;

			for (int pv = 0; pv < Canvas.SIZE; pv++) {
				double z = (pv + 0.5) / (double) Canvas.SIZE;
				for (int pu = 0; pu < Canvas.SIZE; pu++) {
					int idx = pv * Canvas.SIZE + pu;
					int col = texels[idx];
					if (col == 0) {
						continue;
					}

					double x = (pu + 0.5) / (double) Canvas.SIZE;
					if (isUpperStairStep(shape, x, z)) {
						if (upper == null) {
							upper = new int[Canvas.TEXELS];
						}
						upper[idx] = col;
					} else {
						if (lower == null) {
							lower = new int[Canvas.TEXELS];
						}
						lower[idx] = col;
					}
				}
			}

			List<SurfaceCanvas> result = new ArrayList<>(2);
			if (upper != null) {
				result.add(new SurfaceCanvas(new Canvas(upper, canvas.fadeStartTimeMs(), canvas.nextErodeTimeMs()), 1.0F));
			}
			if (lower != null) {
				result.add(new SurfaceCanvas(new Canvas(lower, canvas.fadeStartTimeMs(), canvas.nextErodeTimeMs()), 0.5F));
			}
			return result;
		}

		float surfaceY = (float) planeFor(level, pos, state, face);
		return List.of(new SurfaceCanvas(canvas, surfaceY));
	}

	public static boolean isSeeThrough(BlockState state) {
		return !state.canOcclude();
	}

	public static double planeFor(BlockGetter level, BlockPos pos, BlockState state, int face) {
		if (face != FaceAxes.UP) {
			return 1.0;
		}
		double top = topOf(level, pos, state);
		return top == NONE ? 1.0 : top;
	}

	public static float planeFor(BlockGetter level, BlockPos pos, BlockState state, Direction face) {
		return (float) planeFor(level, pos, state, face.get3DDataValue());
	}
}
