package com.drinfonty.redfx.client.render;

import com.drinfonty.redfx.canvas.FaceAxes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Computes where decals sit on a block's top face (slabs, snow, carpets, etc.)
 * and whether a block is see-through (such as glass).
 */
public final class PaintSurface {
	public static final double NONE = -1.0;

	private PaintSurface() {
	}

	public static double topOf(BlockGetter level, BlockPos pos, BlockState state) {
		if (state.isAir()) {
			return NONE;
		}

		VoxelShape shape = state.getShape(level, pos);
		if (shape.isEmpty()) {
			return NONE;
		}

		AABB bounds = shape.bounds();
		if (bounds.minX > 0.0 || bounds.minY > 0.0 || bounds.minZ > 0.0
			|| bounds.maxX < 1.0 || bounds.maxZ < 1.0 || bounds.maxY > 1.0) {
			return NONE;
		}

		if (Shapes.joinIsNotEmpty(shape, Shapes.box(0.0, 0.0, 0.0, 1.0, bounds.maxY, 1.0), BooleanOp.NOT_SAME)) {
			return NONE;
		}

		return bounds.maxY;
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
