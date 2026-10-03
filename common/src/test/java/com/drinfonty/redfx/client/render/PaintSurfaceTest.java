package com.drinfonty.redfx.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.drinfonty.redfx.canvas.Canvas;

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

		// Snow layer (layer 1) -> 0.125
		BlockState snow = Blocks.SNOW.defaultBlockState();
		assertEquals(0.125, PaintSurface.topOf(level, pos, snow));

		// Foliage / bushes / grass / plants should return NONE (-1.0) and be ignored
		assertEquals(PaintSurface.NONE, PaintSurface.topOf(level, pos, Blocks.SHORT_GRASS.defaultBlockState()));
		assertEquals(PaintSurface.NONE, PaintSurface.topOf(level, pos, Blocks.TALL_GRASS.defaultBlockState()));
		assertEquals(PaintSurface.NONE, PaintSurface.topOf(level, pos, Blocks.FERN.defaultBlockState()));
		assertEquals(PaintSurface.NONE, PaintSurface.topOf(level, pos, Blocks.LARGE_FERN.defaultBlockState()));
		assertEquals(PaintSurface.NONE, PaintSurface.topOf(level, pos, Blocks.DEAD_BUSH.defaultBlockState()));
		assertEquals(PaintSurface.NONE, PaintSurface.topOf(level, pos, Blocks.DANDELION.defaultBlockState()));
		assertEquals(PaintSurface.NONE, PaintSurface.topOf(level, pos, Blocks.POPPY.defaultBlockState()));
		assertEquals(PaintSurface.NONE, PaintSurface.topOf(level, pos, Blocks.ROSE_BUSH.defaultBlockState()));
		assertEquals(PaintSurface.NONE, PaintSurface.topOf(level, pos, Blocks.AZALEA.defaultBlockState()));
		assertEquals(PaintSurface.NONE, PaintSurface.topOf(level, pos, Blocks.FLOWERING_AZALEA.defaultBlockState()));
		assertEquals(PaintSurface.NONE, PaintSurface.topOf(level, pos, Blocks.OAK_SAPLING.defaultBlockState()));
		assertEquals(PaintSurface.NONE, PaintSurface.topOf(level, pos, Blocks.OAK_LEAVES.defaultBlockState()));
		assertEquals(PaintSurface.NONE, PaintSurface.topOf(level, pos, Blocks.VINE.defaultBlockState()));
		assertEquals(PaintSurface.NONE, PaintSurface.topOf(level, pos, Blocks.SUGAR_CANE.defaultBlockState()));
		assertEquals(PaintSurface.NONE, PaintSurface.topOf(level, pos, Blocks.BAMBOO.defaultBlockState()));
		assertEquals(PaintSurface.NONE, PaintSurface.topOf(level, pos, Blocks.WHEAT.defaultBlockState()));

		for (int age = 0; age <= 3; age++) {
			BlockState bush = Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE, age);
			assertEquals(PaintSurface.NONE, PaintSurface.topOf(level, pos, bush));
			org.junit.jupiter.api.Assertions.assertTrue(PaintSurface.isIgnored(bush));
		}
	}

	@Test
	void testCavityAndLanternShapes() {
		BlockGetter level = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
		BlockPos pos = BlockPos.ZERO;

		// 1. Lantern footprint & elevation
		BlockState lantern = Blocks.LANTERN.defaultBlockState();
		assertEquals(0.5625, PaintSurface.topOf(level, pos, lantern), 1e-4);
		assertEquals(0.5625, PaintSurface.surfaceElevationAt(level, pos, lantern, 0.5, 0.5), 1e-4);
		assertEquals(PaintSurface.NONE, PaintSurface.surfaceElevationAt(level, pos, lantern, 0.1, 0.1));

		// 2. Stairs: findEdges for SOUTH should find riser (internal) and lower step (external)
		BlockState stair = Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.NORTH);
		List<PaintSurface.BlockEdge> southEdges = PaintSurface.findEdges(level, pos, stair, Direction.SOUTH, 4);
		assertEquals(2, southEdges.size());

		PaintSurface.BlockEdge riserEdge = southEdges.stream().filter(PaintSurface.BlockEdge::isInternal).findFirst().orElseThrow();
		assertEquals(0.5, riserEdge.colPlane(), 1e-4);
		assertEquals(1.0, riserEdge.colTop(), 1e-4);
		assertEquals(0.5, riserEdge.colBottom(), 1e-4);
		assertEquals(7, riserEdge.edgeV());

		PaintSurface.BlockEdge lowerStepEdge = southEdges.stream().filter(e -> !e.isInternal()).findFirst().orElseThrow();
		assertEquals(1.0, lowerStepEdge.colPlane(), 1e-4);
		assertEquals(0.5, lowerStepEdge.colTop(), 1e-4);
		assertEquals(0.0, lowerStepEdge.colBottom(), 1e-4);
		assertEquals(15, lowerStepEdge.edgeV());

		// 3. Stairs splitCanvas:
		// Upper step blood on topCanvas (v=4) -> SOUTH face decal lands on riser (plane=0.5, surfaceY=1.0)
		int[] topRiserTexels = new int[Canvas.TEXELS];
		topRiserTexels[4 * 16 + 4] = 0xFFFF0000;
		Canvas topRiserCanvas = new Canvas(topRiserTexels, 1000L, 1200L);

		int[] southDecalTexels = new int[Canvas.TEXELS];
		southDecalTexels[2 * 16 + 4] = 0xFFFF0000;
		Canvas southDecalCanvas = new Canvas(southDecalTexels, 1000L, 1200L);

		var riserSplit = PaintSurface.splitCanvas(level, pos, stair, com.drinfonty.redfx.canvas.FaceAxes.SOUTH, southDecalCanvas, topRiserCanvas);
		assertEquals(1, riserSplit.size());
		assertEquals(0.5F, riserSplit.get(0).facePlane(), 1e-4F);
		assertEquals(1.0F, riserSplit.get(0).surfaceY(), 1e-4F);

		// Lower step blood on topCanvas (v=12) -> SOUTH face decal lands on outer lower step (plane=1.0, surfaceY=0.5)
		int[] topLowerTexels = new int[Canvas.TEXELS];
		topLowerTexels[12 * 16 + 4] = 0xFFFF0000;
		Canvas topLowerCanvas = new Canvas(topLowerTexels, 1000L, 1200L);

		var lowerSplit = PaintSurface.splitCanvas(level, pos, stair, com.drinfonty.redfx.canvas.FaceAxes.SOUTH, southDecalCanvas, topLowerCanvas);
		assertEquals(1, lowerSplit.size());
		assertEquals(1.0F, lowerSplit.get(0).facePlane(), 1e-4F);
		assertEquals(0.5F, lowerSplit.get(0).surfaceY(), 1e-4F);

		// 4. Composter: findEdges for SOUTH should find inner north rim (internal) and outer south rim (external)
		BlockState composter = Blocks.COMPOSTER.defaultBlockState();
		List<PaintSurface.BlockEdge> composterSouthEdges = PaintSurface.findEdges(level, pos, composter, Direction.SOUTH, 4);
		assertEquals(2, composterSouthEdges.size());

		PaintSurface.BlockEdge innerWallEdge = composterSouthEdges.stream().filter(PaintSurface.BlockEdge::isInternal).findFirst().orElseThrow();
		assertEquals(0.125, innerWallEdge.colPlane(), 1e-4);
		assertEquals(1.0, innerWallEdge.colTop(), 1e-4);
		assertEquals(0.125, innerWallEdge.colBottom(), 1e-4);

		PaintSurface.BlockEdge outerWallEdge = composterSouthEdges.stream().filter(e -> !e.isInternal()).findFirst().orElseThrow();
		assertEquals(1.0, outerWallEdge.colPlane(), 1e-4);
		assertEquals(1.0, outerWallEdge.colTop(), 1e-4);
		assertEquals(0.125, outerWallEdge.colBottom(), 1e-4);

		// 5. Composter splitCanvas:
		// North rim blood on topCanvas (v=1) -> SOUTH face decal lands on inner north wall (plane=0.125, surfaceY=1.0)
		int[] composterNorthRimTexels = new int[Canvas.TEXELS];
		composterNorthRimTexels[1 * 16 + 4] = 0xFFFF0000;
		Canvas composterTopCanvas = new Canvas(composterNorthRimTexels, 1000L, 1200L);

		var innerSplit = PaintSurface.splitCanvas(level, pos, composter, com.drinfonty.redfx.canvas.FaceAxes.SOUTH, southDecalCanvas, composterTopCanvas);
		assertEquals(1, innerSplit.size());
		assertEquals(0.125F, innerSplit.get(0).facePlane(), 1e-4F);
		assertEquals(1.0F, innerSplit.get(0).surfaceY(), 1e-4F);
	}

	@Test
	void testLecternShape() {
		BlockGetter level = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
		BlockPos pos = BlockPos.ZERO;

		// 1. topOf should return 1.125 for all horizontal facings
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			BlockState lectern = Blocks.LECTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LecternBlock.FACING, facing);
			assertEquals(1.125, PaintSurface.topOf(level, pos, lectern), 1e-4);
		}

		// 2. Lectern facing SOUTH (front is SOUTH at plane=0.9375, top=0.875; back is NORTH at plane=0.125, top=1.125)
		BlockState southLectern = Blocks.LECTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LecternBlock.FACING, Direction.SOUTH);
		int[] texels = new int[Canvas.TEXELS];
		texels[2 * 16 + 8] = 0xFFFF0000;
		Canvas canvas = new Canvas(texels, 1000L, 1200L);

		// UP face
		var upSplit = PaintSurface.splitCanvas(level, pos, southLectern, com.drinfonty.redfx.canvas.FaceAxes.UP, canvas);
		assertEquals(1, upSplit.size());
		assertEquals(1.125F, upSplit.get(0).surfaceY(), 1e-4F);

		// SOUTH face (front lip)
		var southSplit = PaintSurface.splitCanvas(level, pos, southLectern, com.drinfonty.redfx.canvas.FaceAxes.SOUTH, canvas);
		assertEquals(1, southSplit.size());
		assertEquals(0.875F, southSplit.get(0).surfaceY(), 1e-4F);
		assertEquals(0.9375F, southSplit.get(0).facePlane(), 1e-4F);

		// NORTH face (back)
		var northSplit = PaintSurface.splitCanvas(level, pos, southLectern, com.drinfonty.redfx.canvas.FaceAxes.NORTH, canvas);
		assertEquals(1, northSplit.size());
		assertEquals(1.125F, northSplit.get(0).surfaceY(), 1e-4F);
		assertEquals(0.125F, northSplit.get(0).facePlane(), 1e-4F);

		// findEdge on southLectern
		PaintSurface.BlockEdge southEdge = PaintSurface.findEdge(level, pos, southLectern, Direction.SOUTH, 8);
		org.junit.jupiter.api.Assertions.assertNotNull(southEdge);
		assertEquals(0.9375, southEdge.colPlane(), 1e-4);
		assertEquals(0.875, southEdge.colTop(), 1e-4);

		PaintSurface.BlockEdge northEdge = PaintSurface.findEdge(level, pos, southLectern, Direction.NORTH, 8);
		org.junit.jupiter.api.Assertions.assertNotNull(northEdge);
		assertEquals(0.125, northEdge.colPlane(), 1e-4);
		assertEquals(1.125, northEdge.colTop(), 1e-4);

		// 3. Lectern facing NORTH (front is NORTH at plane=0.0625, top=0.875; back is SOUTH at plane=0.875, top=1.125)
		BlockState northLectern = Blocks.LECTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LecternBlock.FACING, Direction.NORTH);
		var northFaceSplit = PaintSurface.splitCanvas(level, pos, northLectern, com.drinfonty.redfx.canvas.FaceAxes.NORTH, canvas);
		assertEquals(1, northFaceSplit.size());
		assertEquals(0.875F, northFaceSplit.get(0).surfaceY(), 1e-4F);
		assertEquals(0.0625F, northFaceSplit.get(0).facePlane(), 1e-4F);

		var southFaceSplit = PaintSurface.splitCanvas(level, pos, northLectern, com.drinfonty.redfx.canvas.FaceAxes.SOUTH, canvas);
		assertEquals(1, southFaceSplit.size());
		assertEquals(1.125F, southFaceSplit.get(0).surfaceY(), 1e-4F);
		assertEquals(0.875F, southFaceSplit.get(0).facePlane(), 1e-4F);

		PaintSurface.BlockEdge northLecternNorthEdge = PaintSurface.findEdge(level, pos, northLectern, Direction.NORTH, 8);
		org.junit.jupiter.api.Assertions.assertNotNull(northLecternNorthEdge);
		assertEquals(0.0625, northLecternNorthEdge.colPlane(), 1e-4);
		assertEquals(0.875, northLecternNorthEdge.colTop(), 1e-4);

		PaintSurface.BlockEdge northLecternSouthEdge = PaintSurface.findEdge(level, pos, northLectern, Direction.SOUTH, 8);
		org.junit.jupiter.api.Assertions.assertNotNull(northLecternSouthEdge);
		assertEquals(0.875, northLecternSouthEdge.colPlane(), 1e-4);
		assertEquals(1.125, northLecternSouthEdge.colTop(), 1e-4);

		// 4. Lectern facing WEST (front is WEST at plane=0.0625, top=0.875; back is EAST at plane=0.875, top=1.125)
		BlockState westLectern = Blocks.LECTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LecternBlock.FACING, Direction.WEST);
		var westFaceSplit = PaintSurface.splitCanvas(level, pos, westLectern, com.drinfonty.redfx.canvas.FaceAxes.WEST, canvas);
		assertEquals(1, westFaceSplit.size());
		assertEquals(0.875F, westFaceSplit.get(0).surfaceY(), 1e-4F);
		assertEquals(0.0625F, westFaceSplit.get(0).facePlane(), 1e-4F);

		var eastFaceSplit = PaintSurface.splitCanvas(level, pos, westLectern, com.drinfonty.redfx.canvas.FaceAxes.EAST, canvas);
		assertEquals(1, eastFaceSplit.size());
		assertEquals(1.125F, eastFaceSplit.get(0).surfaceY(), 1e-4F);
		assertEquals(0.875F, eastFaceSplit.get(0).facePlane(), 1e-4F);

		// 5. Lectern facing EAST (front is EAST at plane=0.9375, top=0.875; back is WEST at plane=0.125, top=1.125)
		BlockState eastLectern = Blocks.LECTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LecternBlock.FACING, Direction.EAST);
		var eastLecternEastSplit = PaintSurface.splitCanvas(level, pos, eastLectern, com.drinfonty.redfx.canvas.FaceAxes.EAST, canvas);
		assertEquals(1, eastLecternEastSplit.size());
		assertEquals(0.875F, eastLecternEastSplit.get(0).surfaceY(), 1e-4F);
		assertEquals(0.9375F, eastLecternEastSplit.get(0).facePlane(), 1e-4F);

		var eastLecternWestSplit = PaintSurface.splitCanvas(level, pos, eastLectern, com.drinfonty.redfx.canvas.FaceAxes.WEST, canvas);
		assertEquals(1, eastLecternWestSplit.size());
		assertEquals(1.125F, eastLecternWestSplit.get(0).surfaceY(), 1e-4F);
		assertEquals(0.125F, eastLecternWestSplit.get(0).facePlane(), 1e-4F);
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

	@Test
	void testAnvilFenceDoorSideSplitCanvas() {
		BlockGetter level = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
		BlockPos pos = BlockPos.ZERO;

		// 1. Fence (Oak Fence post from 0.375 to 0.625)
		BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
		int[] fenceTexels = new int[com.drinfonty.redfx.canvas.Canvas.TEXELS];
		fenceTexels[7 * 16 + 7] = 0xFFFF0000; // pu=7 is inside fence post (7.5/16 = 0.46875)
		var fenceSplit = PaintSurface.splitCanvas(level, pos, fence, com.drinfonty.redfx.canvas.FaceAxes.WEST,
			new com.drinfonty.redfx.canvas.Canvas(fenceTexels, 1000L, 1200L));
		assertEquals(1, fenceSplit.size());
		assertEquals(1.0F, fenceSplit.get(0).surfaceY());
		assertEquals(0.375F, fenceSplit.get(0).facePlane(), 1e-4F);

		// Outside fence post footprint (pu = 1 -> 1.5/16 = 0.09375 < 0.375)
		int[] outsideTexels = new int[com.drinfonty.redfx.canvas.Canvas.TEXELS];
		outsideTexels[7 * 16 + 1] = 0xFFFF0000;
		var outsideSplit = PaintSurface.splitCanvas(level, pos, fence, com.drinfonty.redfx.canvas.FaceAxes.WEST,
			new com.drinfonty.redfx.canvas.Canvas(outsideTexels, 1000L, 1200L));
		org.junit.jupiter.api.Assertions.assertTrue(outsideSplit.isEmpty());

		// 2. Anvil
		// Default anvil faces NORTH (short edges: NORTH/SOUTH, long edges: WEST/EAST)
		BlockState anvil = Blocks.ANVIL.defaultBlockState();
		// Short edge (NORTH face):
		int[] northAnvilTexels = new int[com.drinfonty.redfx.canvas.Canvas.TEXELS];
		northAnvilTexels[2 * 16 + 7] = 0xFFFF0000; // near top of anvil
		var northAnvilSplit = PaintSurface.splitCanvas(level, pos, anvil, com.drinfonty.redfx.canvas.FaceAxes.NORTH,
			new com.drinfonty.redfx.canvas.Canvas(northAnvilTexels, 1000L, 1200L));
		assertEquals(1, northAnvilSplit.size());
		assertEquals(1.0F, northAnvilSplit.get(0).surfaceY());
		assertEquals(0.0F, northAnvilSplit.get(0).facePlane(), 1e-4F);

		// Short edge (SOUTH face):
		int[] southAnvilTexels = new int[com.drinfonty.redfx.canvas.Canvas.TEXELS];
		southAnvilTexels[2 * 16 + 7] = 0xFFFF0000;
		var southAnvilSplit = PaintSurface.splitCanvas(level, pos, anvil, com.drinfonty.redfx.canvas.FaceAxes.SOUTH,
			new com.drinfonty.redfx.canvas.Canvas(southAnvilTexels, 1000L, 1200L));
		assertEquals(1, southAnvilSplit.size());
		assertEquals(1.0F, southAnvilSplit.get(0).surfaceY());
		assertEquals(1.0F, southAnvilSplit.get(0).facePlane(), 1e-4F);

		// Long edge (WEST face):
		int[] westAnvilTexels = new int[com.drinfonty.redfx.canvas.Canvas.TEXELS];
		westAnvilTexels[2 * 16 + 7] = 0xFFFF0000; // near top of anvil
		var westAnvilSplit = PaintSurface.splitCanvas(level, pos, anvil, com.drinfonty.redfx.canvas.FaceAxes.WEST,
			new com.drinfonty.redfx.canvas.Canvas(westAnvilTexels, 1000L, 1200L));
		assertEquals(1, westAnvilSplit.size());
		assertEquals(1.0F, westAnvilSplit.get(0).surfaceY());
		assertEquals(0.1875F, westAnvilSplit.get(0).facePlane(), 1e-4F);

		// Long edge (EAST face):
		int[] eastAnvilTexels = new int[com.drinfonty.redfx.canvas.Canvas.TEXELS];
		eastAnvilTexels[2 * 16 + 7] = 0xFFFF0000;
		var eastAnvilSplit = PaintSurface.splitCanvas(level, pos, anvil, com.drinfonty.redfx.canvas.FaceAxes.EAST,
			new com.drinfonty.redfx.canvas.Canvas(eastAnvilTexels, 1000L, 1200L));
		assertEquals(1, eastAnvilSplit.size());
		assertEquals(1.0F, eastAnvilSplit.get(0).surfaceY());
		assertEquals(0.8125F, eastAnvilSplit.get(0).facePlane(), 1e-4F);

		// Anvil facing EAST (short edges: WEST/EAST, long edges: NORTH/SOUTH)
		BlockState eastAnvil = Blocks.ANVIL.defaultBlockState()
			.setValue(net.minecraft.world.level.block.AnvilBlock.FACING, Direction.EAST);

		// Long edge (NORTH face):
		var eastAnvilNorthSplit = PaintSurface.splitCanvas(level, pos, eastAnvil, com.drinfonty.redfx.canvas.FaceAxes.NORTH,
			new com.drinfonty.redfx.canvas.Canvas(northAnvilTexels, 1000L, 1200L));
		assertEquals(1, eastAnvilNorthSplit.size());
		assertEquals(1.0F, eastAnvilNorthSplit.get(0).surfaceY());
		assertEquals(0.1875F, eastAnvilNorthSplit.get(0).facePlane(), 1e-4F);

		// Long edge (SOUTH face):
		var eastAnvilSouthSplit = PaintSurface.splitCanvas(level, pos, eastAnvil, com.drinfonty.redfx.canvas.FaceAxes.SOUTH,
			new com.drinfonty.redfx.canvas.Canvas(southAnvilTexels, 1000L, 1200L));
		assertEquals(1, eastAnvilSouthSplit.size());
		assertEquals(1.0F, eastAnvilSouthSplit.get(0).surfaceY());
		assertEquals(0.8125F, eastAnvilSouthSplit.get(0).facePlane(), 1e-4F);

		// Short edge (WEST face):
		var eastAnvilWestSplit = PaintSurface.splitCanvas(level, pos, eastAnvil, com.drinfonty.redfx.canvas.FaceAxes.WEST,
			new com.drinfonty.redfx.canvas.Canvas(westAnvilTexels, 1000L, 1200L));
		assertEquals(1, eastAnvilWestSplit.size());
		assertEquals(1.0F, eastAnvilWestSplit.get(0).surfaceY());
		assertEquals(0.0F, eastAnvilWestSplit.get(0).facePlane(), 1e-4F);

		// Short edge (EAST face):
		var eastAnvilEastSplit = PaintSurface.splitCanvas(level, pos, eastAnvil, com.drinfonty.redfx.canvas.FaceAxes.EAST,
			new com.drinfonty.redfx.canvas.Canvas(eastAnvilTexels, 1000L, 1200L));
		assertEquals(1, eastAnvilEastSplit.size());
		assertEquals(1.0F, eastAnvilEastSplit.get(0).surfaceY());
		assertEquals(1.0F, eastAnvilEastSplit.get(0).facePlane(), 1e-4F);

		// 3. Door
		BlockState door = Blocks.OAK_DOOR.defaultBlockState();
		int[] doorTexels = new int[com.drinfonty.redfx.canvas.Canvas.TEXELS];
		doorTexels[7 * 16 + 7] = 0xFFFF0000;
		var doorSplit = PaintSurface.splitCanvas(level, pos, door, com.drinfonty.redfx.canvas.FaceAxes.SOUTH,
			new com.drinfonty.redfx.canvas.Canvas(doorTexels, 1000L, 1200L));
		assertEquals(1, doorSplit.size());
		assertEquals(1.0F, doorSplit.get(0).surfaceY());
	}
}
