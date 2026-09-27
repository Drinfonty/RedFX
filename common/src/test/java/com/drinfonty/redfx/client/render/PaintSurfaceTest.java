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

	@Test
	void testStairHorizontalSplitCanvas() {
		BlockGetter level = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
		BlockPos pos = BlockPos.ZERO;

		// Straight bottom stair facing North:
		// Upper step is z < 0.5
		// Lower step is z > 0.5
		BlockState stair = Blocks.OAK_STAIRS.defaultBlockState()
			.setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.NORTH)
			.setValue(net.minecraft.world.level.block.StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.BOTTOM);

		// NORTH face (z = 0.01) is upper step -> surfaceY = 1.0F
		int[] northTexels = new int[com.drinfonty.redfx.canvas.Canvas.TEXELS];
		northTexels[0 * 16 + 4] = 0xFFFF0000;
		var northSplit = PaintSurface.splitCanvas(level, pos, stair, com.drinfonty.redfx.canvas.FaceAxes.NORTH,
			new com.drinfonty.redfx.canvas.Canvas(northTexels, 1000L, 1200L));
		assertEquals(1, northSplit.size());
		assertEquals(1.0F, northSplit.get(0).surfaceY());

		// SOUTH face (z = 0.99) is lower step -> surfaceY = 0.5F
		int[] southTexels = new int[com.drinfonty.redfx.canvas.Canvas.TEXELS];
		southTexels[0 * 16 + 4] = 0xFFFF0000;
		var southSplit = PaintSurface.splitCanvas(level, pos, stair, com.drinfonty.redfx.canvas.FaceAxes.SOUTH,
			new com.drinfonty.redfx.canvas.Canvas(southTexels, 1000L, 1200L));
		assertEquals(1, southSplit.size());
		assertEquals(0.5F, southSplit.get(0).surfaceY());

		// EAST face: pu < 8 is lower step (z > 0.5), pu >= 8 is upper step (z < 0.5)
		int[] eastTexels = new int[com.drinfonty.redfx.canvas.Canvas.TEXELS];
		eastTexels[0 * 16 + 2] = 0xFFFF0000; // lower step
		eastTexels[0 * 16 + 12] = 0xFF00FF00; // upper step
		var eastSplit = PaintSurface.splitCanvas(level, pos, stair, com.drinfonty.redfx.canvas.FaceAxes.EAST,
			new com.drinfonty.redfx.canvas.Canvas(eastTexels, 1000L, 1200L));
		assertEquals(2, eastSplit.size());
		assertEquals(1.0F, eastSplit.get(0).surfaceY());
		assertEquals(0xFF00FF00, eastSplit.get(0).canvas().texels()[0 * 16 + 12]);
		assertEquals(0.5F, eastSplit.get(1).surfaceY());
		assertEquals(0xFFFF0000, eastSplit.get(1).canvas().texels()[0 * 16 + 2]);
	}

	@Test
	void testDirtPathAndSnow() {
		BlockGetter level = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
		BlockPos pos = BlockPos.ZERO;

		// Dirt path -> 0.9375 (15/16)
		BlockState path = Blocks.DIRT_PATH.defaultBlockState();
		assertEquals(0.9375, PaintSurface.topOf(level, pos, path), 0.001);
		assertEquals(0.9375, PaintSurface.surfaceElevationAt(level, pos, path, 0.5, 0.5), 0.001);
		assertEquals(0.0, PaintSurface.surfaceBottomAt(level, pos, path, 0.5, 0.5), 0.001);

		// Snow layer (1 layer) -> 0.125 (2/16)
		BlockState snow = Blocks.SNOW.defaultBlockState();
		assertEquals(0.125, PaintSurface.topOf(level, pos, snow), 0.001);
	}

	@Test
	void testGenericSurfaceElevationAtAndBottom() {
		BlockGetter level = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
		BlockPos pos = BlockPos.ZERO;

		// Top slab: top is 1.0, bottom is 0.5
		BlockState topSlab = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
		assertEquals(1.0, PaintSurface.surfaceElevationAt(level, pos, topSlab, 0.5, 0.5));
		assertEquals(0.5, PaintSurface.surfaceBottomAt(level, pos, topSlab, 0.5, 0.5));

		// Bottom slab: top is 0.5, bottom is 0.0
		BlockState bottomSlab = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
		assertEquals(0.5, PaintSurface.surfaceElevationAt(level, pos, bottomSlab, 0.5, 0.5));
		assertEquals(0.0, PaintSurface.surfaceBottomAt(level, pos, bottomSlab, 0.5, 0.5));

		// Bottom stair facing North: upper step at z < 0.5, lower step at z > 0.5
		BlockState stair = Blocks.OAK_STAIRS.defaultBlockState()
			.setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.NORTH)
			.setValue(net.minecraft.world.level.block.StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.BOTTOM);
		assertEquals(1.0, PaintSurface.surfaceElevationAt(level, pos, stair, 0.5, 0.25));
		assertEquals(0.0, PaintSurface.surfaceBottomAt(level, pos, stair, 0.5, 0.25));
		assertEquals(0.5, PaintSurface.surfaceElevationAt(level, pos, stair, 0.5, 0.75));
		assertEquals(0.0, PaintSurface.surfaceBottomAt(level, pos, stair, 0.5, 0.75));
	}

	@Test
	void testChestFindEdgeAndSplitCanvas() {
		BlockGetter level = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
		BlockPos pos = BlockPos.ZERO;
		BlockState chest = Blocks.CHEST.defaultBlockState();

		// Chest North edge: minZ is 1/16 -> edgeV = 1, colPlane = 0.0625, colTop = 0.875
		PaintSurface.BlockEdge northEdge = PaintSurface.findEdge(level, pos, chest, Direction.NORTH, 7);
		org.junit.jupiter.api.Assertions.assertNotNull(northEdge);
		assertEquals(7, northEdge.edgeU());
		assertEquals(1, northEdge.edgeV());
		assertEquals(0.875, northEdge.colTop(), 1e-4);
		assertEquals(0.0625, northEdge.colPlane(), 1e-4);

		// Chest South edge: maxZ is 15/16 -> edgeV = 14, colPlane = 0.9375
		PaintSurface.BlockEdge southEdge = PaintSurface.findEdge(level, pos, chest, Direction.SOUTH, 7);
		org.junit.jupiter.api.Assertions.assertNotNull(southEdge);
		assertEquals(7, southEdge.edgeU());
		assertEquals(14, southEdge.edgeV());
		assertEquals(0.9375, southEdge.colPlane(), 1e-4);

		// Chest West edge: minX is 1/16 -> edgeU = 1, colPlane = 0.0625
		PaintSurface.BlockEdge westEdge = PaintSurface.findEdge(level, pos, chest, Direction.WEST, 7);
		org.junit.jupiter.api.Assertions.assertNotNull(westEdge);
		assertEquals(1, westEdge.edgeU());
		assertEquals(7, westEdge.edgeV());
		assertEquals(0.0625, westEdge.colPlane(), 1e-4);

		// Chest East edge: maxX is 15/16 -> edgeU = 14, colPlane = 0.9375
		PaintSurface.BlockEdge eastEdge = PaintSurface.findEdge(level, pos, chest, Direction.EAST, 7);
		org.junit.jupiter.api.Assertions.assertNotNull(eastEdge);
		assertEquals(14, eastEdge.edgeU());
		assertEquals(7, eastEdge.edgeV());
		assertEquals(0.9375, eastEdge.colPlane(), 1e-4);

		// Outside chest footprint (coord = 0): findEdge returns null
		org.junit.jupiter.api.Assertions.assertNull(PaintSurface.findEdge(level, pos, chest, Direction.NORTH, 0));

		// splitCanvas on chest North face:
		int[] texels = new int[com.drinfonty.redfx.canvas.Canvas.TEXELS];
		texels[0 * 16 + 7] = 0xFFFF0000;
		var split = PaintSurface.splitCanvas(level, pos, chest, com.drinfonty.redfx.canvas.FaceAxes.NORTH,
			new com.drinfonty.redfx.canvas.Canvas(texels, 1000L, 1200L));
		assertEquals(1, split.size());
		assertEquals(0.875F, split.get(0).surfaceY(), 1e-4F);
		assertEquals(0.0625F, split.get(0).facePlane(), 1e-4F);
	}
}
