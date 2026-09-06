package com.drinfonty.redfx.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

class PaintSurfaceTest {
	@BeforeAll
	static void init() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void testTopOf() {
		BlockGetter level = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;

		BlockPos pos = BlockPos.ZERO;

		// Stone (Full Block) -> 1.0
		BlockState stone = Blocks.STONE.defaultBlockState();
		assertEquals(1.0, PaintSurface.topOf(level, pos, stone));

		// Bottom Slab -> 0.5
		BlockState bottomSlab = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
		assertEquals(0.5, PaintSurface.topOf(level, pos, bottomSlab));

		// Top Slab -> 1.0
		BlockState topSlab = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
		assertEquals(1.0, PaintSurface.topOf(level, pos, topSlab));

		// Double Slab -> 1.0
		BlockState doubleSlab = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
		assertEquals(1.0, PaintSurface.topOf(level, pos, doubleSlab));

		// Chest -> 0.875
		BlockState chest = Blocks.CHEST.defaultBlockState();
		assertEquals(0.875, PaintSurface.topOf(level, pos, chest));

		// Ender Chest -> 0.875
		BlockState enderChest = Blocks.ENDER_CHEST.defaultBlockState();
		assertEquals(0.875, PaintSurface.topOf(level, pos, enderChest));

		// Trapped Chest -> 0.875
		BlockState trappedChest = Blocks.TRAPPED_CHEST.defaultBlockState();
		assertEquals(0.875, PaintSurface.topOf(level, pos, trappedChest));

		// Moss Carpet -> 0.0625
		BlockState carpet = Blocks.MOSS_CARPET.defaultBlockState();
		assertEquals(0.0625, PaintSurface.topOf(level, pos, carpet));

		// Air should return NONE (-1.0)
		assertEquals(PaintSurface.NONE, PaintSurface.topOf(level, pos, Blocks.AIR.defaultBlockState()));

		// Stairs should return 1.0
		assertEquals(1.0, PaintSurface.topOf(level, pos, Blocks.OAK_STAIRS.defaultBlockState()));

		// Foliage / plants / short grass should return NONE (-1.0)
		assertEquals(PaintSurface.NONE, PaintSurface.topOf(level, pos, Blocks.SHORT_GRASS.defaultBlockState()));
		assertEquals(PaintSurface.NONE, PaintSurface.topOf(level, pos, Blocks.DANDELION.defaultBlockState()));
		assertEquals(PaintSurface.NONE, PaintSurface.topOf(level, pos, Blocks.FERN.defaultBlockState()));
	}

	@Test
	void testStairSplitCanvas() {
		BlockGetter level = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
		BlockPos pos = BlockPos.ZERO;

		// Straight bottom stair facing North:
		// Upper step is z < 0.5 (pv < 8)
		// Lower step is z > 0.5 (pv >= 8)
		BlockState stair = Blocks.OAK_STAIRS.defaultBlockState()
			.setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.NORTH)
			.setValue(net.minecraft.world.level.block.StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.BOTTOM);

		int[] texels = new int[com.drinfonty.redfx.canvas.Canvas.TEXELS];
		// Put pixel at pu=4, pv=2 (upper step)
		texels[2 * 16 + 4] = 0xFFFF0000;
		// Put pixel at pu=4, pv=12 (lower step)
		texels[12 * 16 + 4] = 0xFFFF0000;

		com.drinfonty.redfx.canvas.Canvas canvas = new com.drinfonty.redfx.canvas.Canvas(texels, 1000L, 1200L);
		var split = PaintSurface.splitCanvas(level, pos, stair, com.drinfonty.redfx.canvas.FaceAxes.UP, canvas);

		assertEquals(2, split.size());
		assertEquals(1.0F, split.get(0).surfaceY());
		assertEquals(0xFFFF0000, split.get(0).canvas().texels()[2 * 16 + 4]);
		assertEquals(0, split.get(0).canvas().texels()[12 * 16 + 4]);

		assertEquals(0.5F, split.get(1).surfaceY());
		assertEquals(0, split.get(1).canvas().texels()[2 * 16 + 4]);
		assertEquals(0xFFFF0000, split.get(1).canvas().texels()[12 * 16 + 4]);
	}
}
