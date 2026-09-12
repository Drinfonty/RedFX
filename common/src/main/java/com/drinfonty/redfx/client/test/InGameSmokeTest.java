package com.drinfonty.redfx.client.test;

import java.io.File;
import java.util.List;

import com.drinfonty.redfx.RedfxMod;
import com.drinfonty.redfx.canvas.BloodSplatter;
import com.drinfonty.redfx.canvas.Canvas;
import com.drinfonty.redfx.canvas.FaceAxes;
import com.drinfonty.redfx.client.ClientCanvasStore;
import com.drinfonty.redfx.client.render.CanvasMesher;
import com.drinfonty.redfx.client.render.PaintGeometry;
import com.drinfonty.redfx.client.render.PaintQuad;
import com.drinfonty.redfx.client.render.PaintSurface;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Headless in-game automated test runner active only when -Dredfx.smokeTest=true is passed.
 * Tests decal placement, mesh generation, coordinate projection, and stair splitting in a live client level.
 */
public final class InGameSmokeTest {
	public static final boolean ENABLED = Boolean.getBoolean("redfx.smokeTest");
	private static boolean ran = false;

	private InGameSmokeTest() {
	}

	public static void tick(Minecraft client) {
		if (!ENABLED || ran || client.level == null || client.player == null) {
			return;
		}

		// Wait until player is loaded and client tick is ready
		if (client.player.tickCount < 10) {
			return;
		}

		ran = true;
		RedfxMod.LOGGER.info("=================================================");
		RedfxMod.LOGGER.info("Starting RedFX In-Game Automated Smoke Test Suite");
		RedfxMod.LOGGER.info("=================================================");

		try {
			runSuite(client);
			RedfxMod.LOGGER.info("=================================================");
			RedfxMod.LOGGER.info("ALL REDFX IN-GAME SMOKE TESTS PASSED CLEANLY!");
			RedfxMod.LOGGER.info("=================================================");
			
			// Schedule client shutdown after successful test
			new Thread(() -> {
				try {
					Thread.sleep(1500);
					client.execute(() -> {
						client.stop();
					});
				} catch (Exception ignored) {
				}
			}, "RedFX-SmokeTest-Shutdown").start();
		} catch (Throwable t) {
			RedfxMod.LOGGER.error("REDFX IN-GAME SMOKE TEST FAILED!", t);
			System.err.println("FATAL: REDFX IN-GAME SMOKE TEST FAILED!");
			t.printStackTrace(System.err);
			System.exit(1);
		}
	}

	private static void runSuite(Minecraft client) {
		ClientCanvasStore store = ClientCanvasStore.get();
		store.clearAll();

		BlockPos origin = client.player.blockPosition().relative(client.player.getDirection(), 2);
		int red = 0xFFFF0000;

		// 1. Test Solid Block (Stone)
		RedfxMod.LOGGER.info("Testing paint on solid block...");
		BlockPos stonePos = origin.offset(0, 0, 0);
		BlockState stoneState = Blocks.STONE.defaultBlockState();
		int[] stoneTexels = new int[Canvas.TEXELS];
		BloodSplatter.stamp(stoneTexels, 8, 8, red, 1, 1.0f);
		Canvas stoneCanvas = new Canvas(stoneTexels, System.currentTimeMillis() + 60000L);
		store.put(stonePos, FaceAxes.UP, stoneCanvas);

		if (!store.isPainted(stonePos)) {
			throw new AssertionError("Stone block at " + stonePos + " was not marked as painted!");
		}
		List<PaintQuad> stoneQuads = CanvasMesher.mesh(store.get(stonePos, FaceAxes.UP).texels(), FaceAxes.UP);
		if (stoneQuads.isEmpty()) {
			throw new AssertionError("Meshing stone canvas produced 0 quads!");
		}

		// 2. Test Stair Splitting
		RedfxMod.LOGGER.info("Testing paint and split on oak stairs...");
		BlockPos stairPos = origin.offset(1, 0, 0);
		BlockState stairState = Blocks.OAK_STAIRS.defaultBlockState()
			.setValue(StairBlock.FACING, Direction.NORTH)
			.setValue(StairBlock.HALF, Half.BOTTOM);

		int[] stairTexels = new int[Canvas.TEXELS];
		stairTexels[2 * 16 + 4] = red;  // pv=2 (upper step)
		stairTexels[12 * 16 + 4] = red; // pv=12 (lower step)
		Canvas stairCanvas = new Canvas(stairTexels, System.currentTimeMillis() + 60000L);
		store.put(stairPos, FaceAxes.UP, stairCanvas);

		List<PaintSurface.SurfaceCanvas> split = PaintSurface.splitCanvas(client.level, stairPos, stairState, FaceAxes.UP, stairCanvas);
		if (split.size() != 2) {
			throw new AssertionError("Expected stair to split into 2 surfaces, got " + split.size());
		}
		if (Math.abs(split.get(0).surfaceY() - 1.0F) > 0.001F) {
			throw new AssertionError("Expected upper stair step Y=1.0, got " + split.get(0).surfaceY());
		}
		if (Math.abs(split.get(1).surfaceY() - 0.5F) > 0.001F) {
			throw new AssertionError("Expected lower stair step Y=0.5, got " + split.get(1).surfaceY());
		}

		// 3. Test Geometry Projection
		float[] corners = new float[12];
		PaintGeometry.corners(stoneQuads.get(0), corners, 1.0F);
		for (float c : corners) {
			if (Float.isNaN(c) || Float.isInfinite(c)) {
				throw new AssertionError("PaintGeometry generated invalid corner coordinate: " + c);
			}
		}

		// 4. Test Slabs & Carpets Surface Heights
		RedfxMod.LOGGER.info("Testing surface height detection...");
		BlockState bottomSlab = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
		if (Math.abs(PaintSurface.topOf(client.level, origin, bottomSlab) - 0.5) > 0.001) {
			throw new AssertionError("Bottom slab height mismatch!");
		}

		BlockState carpet = Blocks.MOSS_CARPET.defaultBlockState();
		if (Math.abs(PaintSurface.topOf(client.level, origin, carpet) - 0.0625) > 0.001) {
			throw new AssertionError("Carpet height mismatch!");
		}

		// 5. Test See-Through Glass Property
		RedfxMod.LOGGER.info("Testing see-through block detection...");
		if (!PaintSurface.isSeeThrough(Blocks.GLASS.defaultBlockState())) {
			throw new AssertionError("Glass was expected to be see-through!");
		}
		if (PaintSurface.isSeeThrough(Blocks.STONE.defaultBlockState())) {
			throw new AssertionError("Stone was not expected to be see-through!");
		}

		// 6. Test Non-Paintable Foliage
		if (PaintSurface.topOf(client.level, origin, Blocks.SHORT_GRASS.defaultBlockState()) != PaintSurface.NONE) {
			throw new AssertionError("Short grass should not have a top paint surface!");
		}
	}
}
