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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;

/**
 * Headless in-game automated test runner active only when -Dredfx.smokeTest=true is passed.
 * Builds an on-the-fly arena, summons a test mob, performs a live attack, and verifies blood splatters.
 */
public final class InGameSmokeTest {
	public static final boolean ENABLED = Boolean.getBoolean("redfx.smokeTest")
			|| "true".equalsIgnoreCase(System.getenv("REDFX_SMOKE_TEST"));
	private static volatile boolean playerSpawned = false;
	private static int state = 0;
	private static int tickCounter = 0;
	private static BlockPos testOrigin = null;
	private static LivingEntity targetMob = null;

	private InGameSmokeTest() {
	}

	public static void onPlayerSpawned() {
		if (!playerSpawned) {
			playerSpawned = true;
			RedfxMod.LOGGER.info("[SmokeTest] Intercepted player spawn event: player joined client level!");
		}
	}

	public static void tick(Minecraft client) {
		if (!ENABLED || state == 99) {
			return;
		}

		if (state == 0) {
			// Wait until the player spawn event has fired, player & level exist, and loading screen is closed
			if (!playerSpawned || hasActiveScreen(client) || client.player == null || client.level == null) {
				return;
			}

			state = 1;
			RedfxMod.LOGGER.info("=================================================");
			RedfxMod.LOGGER.info("Player spawned & in gameplay. Starting RedFX Smoke Test Suite");
			RedfxMod.LOGGER.info("=================================================");

			try {
				BlockPos origin = client.player.blockPosition().relative(client.player.getDirection(), 2);
				testOrigin = origin;
				runGeometricUnitTests(client, origin);
				RedfxMod.LOGGER.info("Geometric invariant unit tests passed cleanly!");

				setupCombatArena(client, origin);
				state = 2;
				tickCounter = 0;
			} catch (Throwable t) {
				fail(t);
			}
			return;
		}

		if (state == 2) {
			// Wait for server to spawn the test mob and sync to client
			tickCounter++;
			if (targetMob == null) {
				AABB box = new AABB(testOrigin).inflate(4.0);
				for (Entity entity : client.level.entitiesForRendering()) {
					if (entity instanceof LivingEntity living && living.isAlive() && !(living instanceof Player)) {
						if (box.contains(living.position())) {
							targetMob = living;
							break;
						}
					}
				}
			}

			if (targetMob == null) {
				if (tickCounter > 40) {
					fail(new AssertionError("Timeout waiting for summoned test mob to appear on client!"));
				}
				return;
			}

			try {
				RedfxMod.LOGGER.info("Found test mob {}. Aiming and executing attack...",
					targetMob.getType().getDescriptionId());

				// Equip diamond sword and look towards the mob
				client.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
				client.player.setXRot(15.0f);

				// Perform client attack
				client.gameMode.attack(client.player, targetMob);
				client.player.swing(InteractionHand.MAIN_HAND);

				RedfxMod.LOGGER.info("Executed attack on mob. Awaiting blood particle flight and splatter...");
				state = 3;
				tickCounter = 0;
			} catch (Throwable t) {
				fail(t);
			}
			return;
		}

		if (state == 3) {
			// Wait for blood particles to fly through the air, land, and stamp into ClientCanvasStore
			tickCounter++;
			if (tickCounter < 15) {
				return;
			}

			ClientCanvasStore store = ClientCanvasStore.get();
			boolean foundBlood = false;
			for (BlockPos pos : BlockPos.betweenClosed(testOrigin.offset(-2, -1, -2), testOrigin.offset(2, 1, 2))) {
				if (store.isPainted(pos)) {
					foundBlood = true;
					RedfxMod.LOGGER.info("Found natural blood splatter at {}", pos);
					break;
				}
			}

			if (!foundBlood) {
				if (tickCounter < 35) {
					return; // Allow a few more ticks if particles are still falling
				}
				fail(new AssertionError("Attacking mob did not produce any blood decals on surrounding blocks!"));
				return;
			}

			RedfxMod.LOGGER.info("=================================================");
			RedfxMod.LOGGER.info("VERIFIED: Combat hit produced natural blood decals on the ground!");
			RedfxMod.LOGGER.info("ALL REDFX IN-GAME SMOKE TESTS PASSED CLEANLY!");
			RedfxMod.LOGGER.info("=================================================");

			state = 4;
			tickCounter = 0;
			return;
		}

		if (state == 4) {
			// Wait 5 render ticks so the graphics engine rasterizes the decals and damaged mob
			tickCounter++;
			if (tickCounter < 5) {
				return;
			}

			captureScreenshot(client);
			state = 99;

			// Schedule client shutdown after screenshot is captured
			new Thread(() -> {
				try {
					Thread.sleep(1500);
					client.execute(() -> {
						client.stop();
					});
				} catch (Exception ignored) {
				}
			}, "RedFX-SmokeTest-Shutdown").start();
		}
	}

	private static void fail(Throwable t) {
		RedfxMod.LOGGER.error("REDFX IN-GAME SMOKE TEST FAILED!", t);
		System.err.println("FATAL: REDFX IN-GAME SMOKE TEST FAILED!");
		t.printStackTrace(System.err);
		System.exit(1);
	}

	private static void setupCombatArena(Minecraft client, BlockPos origin) {
		ClientCanvasStore.get().clearAll();
		Direction forward = client.player.getDirection();
		Direction left = forward.getClockWise();

		int ox = origin.getX();
		int oy = origin.getY();
		int oz = origin.getZ();

		// Set up blocks on client
		BlockPos stonePos = origin;
		BlockPos stairPos = origin.relative(forward, 1);
		BlockPos slabPos = origin.relative(left, 1);

		client.level.setBlock(stonePos, Blocks.STONE.defaultBlockState(), 3);
		client.level.setBlock(stairPos, Blocks.OAK_STAIRS.defaultBlockState()
			.setValue(StairBlock.FACING, forward.getOpposite())
			.setValue(StairBlock.HALF, Half.BOTTOM), 3);
		client.level.setBlock(slabPos, Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
			.setValue(SlabBlock.TYPE, SlabType.BOTTOM), 3);

		// Synchronize arena and summon mob via server
		var server = client.getSingleplayerServer();
		if (server != null) {
			var commands = server.getCommands();
			var source = server.createCommandSourceStack();

			// 1. Foundation under arena
			commands.performPrefixedCommand(source, String.format(java.util.Locale.ROOT,
				"fill %d %d %d %d %d %d stone", ox - 2, oy - 1, oz - 2, ox + 2, oy - 1, oz + 2));

			// 2. Center stone block
			commands.performPrefixedCommand(source, String.format(java.util.Locale.ROOT,
				"setblock %d %d %d stone", ox, oy, oz));

			// 3. Oak stairs behind
			commands.performPrefixedCommand(source, String.format(java.util.Locale.ROOT,
				"setblock %d %d %d oak_stairs[facing=%s,half=bottom]",
				stairPos.getX(), stairPos.getY(), stairPos.getZ(), forward.getOpposite().getName()));

			// 4. Smooth stone slab adjacent
			commands.performPrefixedCommand(source, String.format(java.util.Locale.ROOT,
				"setblock %d %d %d smooth_stone_slab[type=bottom]",
				slabPos.getX(), slabPos.getY(), slabPos.getZ()));

			// 5. Ensure player has diamond sword
			commands.performPrefixedCommand(source, "item replace entity @p weapon.mainhand with diamond_sword");

			// 6. Summon Husk standing on top of center block (never burns in sunlight)
			commands.performPrefixedCommand(source, String.format(java.util.Locale.ROOT,
				"summon husk %d %d %d {NoAI:1b,Silent:1b}", ox, oy + 1, oz));
		}
	}

	private static boolean hasActiveScreen(Minecraft client) {
		try {
			// Modern 26.x: client.gui.screen()
			if (client.gui != null) {
				try {
					java.lang.reflect.Method screenMethod = client.gui.getClass().getMethod("screen");
					return screenMethod.invoke(client.gui) != null;
				} catch (NoSuchMethodException ignored) {
				}
			}

			// Legacy 1.21.x: client.screen
			java.lang.reflect.Field screenField = Minecraft.class.getField("screen");
			return screenField.get(client) != null;
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static void captureScreenshot(Minecraft client) {
		try {
			try {
				java.lang.reflect.Method modernGrab = net.minecraft.client.Screenshot.class.getMethod("grab", Minecraft.class, boolean.class);
				modernGrab.invoke(null, client, false);
				RedfxMod.LOGGER.info("Called Minecraft Screenshot.grab(client, false) successfully!");
				return;
			} catch (NoSuchMethodException ignored) {
			}

			java.lang.reflect.Method getTarget = client.getClass().getMethod("getMainRenderTarget");
			Object target = getTarget.invoke(client);
			java.lang.reflect.Method legacyGrab = net.minecraft.client.Screenshot.class.getMethod("grab", File.class, target.getClass(), java.util.function.Consumer.class);
			legacyGrab.invoke(null, client.gameDirectory, target, (java.util.function.Consumer<net.minecraft.network.chat.Component>) msg -> {});
			RedfxMod.LOGGER.info("Called legacy Screenshot.grab() successfully!");
		} catch (Throwable t) {
			RedfxMod.LOGGER.warn("Failed to capture in-game screenshot: ", t);
		}
	}

	private static void runGeometricUnitTests(Minecraft client, BlockPos origin) {
		int red = 0xFFFF0000;
		int[] stoneTexels = new int[Canvas.TEXELS];
		BloodSplatter.stamp(stoneTexels, 8, 8, red, 2, 1.0f);
		List<PaintQuad> stoneQuads = CanvasMesher.mesh(stoneTexels, FaceAxes.UP);
		if (stoneQuads.isEmpty()) {
			throw new AssertionError("Meshing stone canvas produced 0 quads!");
		}

		// Oak stairs split verification
		BlockState stairState = Blocks.OAK_STAIRS.defaultBlockState()
			.setValue(StairBlock.FACING, Direction.NORTH)
			.setValue(StairBlock.HALF, Half.BOTTOM);
		int[] stairTexels = new int[Canvas.TEXELS];
		stairTexels[2 * 16 + 4] = red;
		stairTexels[12 * 16 + 4] = red;
		Canvas stairCanvas = new Canvas(stairTexels, System.currentTimeMillis() + 60000L);
		List<PaintSurface.SurfaceCanvas> split = PaintSurface.splitCanvas(client.level, origin, stairState, FaceAxes.UP, stairCanvas);
		if (split.size() != 2) {
			throw new AssertionError("Expected stair to split into 2 surfaces, got " + split.size());
		}
		if (Math.abs(split.get(0).surfaceY() - 1.0F) > 0.001F) {
			throw new AssertionError("Expected upper stair step Y=1.0, got " + split.get(0).surfaceY());
		}
		if (Math.abs(split.get(1).surfaceY() - 0.5F) > 0.001F) {
			throw new AssertionError("Expected lower stair step Y=0.5, got " + split.get(1).surfaceY());
		}

		// Corners projection
		float[] corners = new float[12];
		PaintGeometry.corners(stoneQuads.get(0), corners, 1.0F);
		for (float c : corners) {
			if (Float.isNaN(c) || Float.isInfinite(c)) {
				throw new AssertionError("PaintGeometry generated invalid corner coordinate: " + c);
			}
		}

		// Surface height detection
		BlockState bottomSlab = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
		if (Math.abs(PaintSurface.topOf(client.level, origin, bottomSlab) - 0.5) > 0.001) {
			throw new AssertionError("Bottom slab height mismatch!");
		}

		BlockState carpet = Blocks.MOSS_CARPET.defaultBlockState();
		if (Math.abs(PaintSurface.topOf(client.level, origin, carpet) - 0.0625) > 0.001) {
			throw new AssertionError("Carpet height mismatch!");
		}

		// See-through block detection
		if (!PaintSurface.isSeeThrough(Blocks.GLASS.defaultBlockState())) {
			throw new AssertionError("Glass was expected to be see-through!");
		}
		if (PaintSurface.isSeeThrough(Blocks.STONE.defaultBlockState())) {
			throw new AssertionError("Stone was not expected to be see-through!");
		}

		// Foliage non-paintable
		if (PaintSurface.topOf(client.level, origin, Blocks.SHORT_GRASS.defaultBlockState()) != PaintSurface.NONE) {
			throw new AssertionError("Short grass should not have a top paint surface!");
		}
	}
}
