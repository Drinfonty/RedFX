package com.drinfonty.redfx.client;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.drinfonty.redfx.client.particle.BloodParticle;
import com.drinfonty.redfx.config.RedfxConfig;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Reproduces and measures GitHub Issue #4 (severe frame-drop on a cave spider farm
 * when edge dripping repeatedly re-scans and duplicates drips across already-stained blocks).
 */
class SpiderFarmBenchmarkTest {
	private ClientCanvasStore store;
	private ClientCanvasStore.RenderDispatcher originalDispatcher;
	private boolean origDripOverEdges;
	private boolean origGradualSplatter;
	private int origGrowthDelayTicks;
	private int origLifetimeSeconds;

	@BeforeAll
	static void initAll() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@BeforeEach
	void setUp() {
		store = ClientCanvasStore.get();
		store.clearAll();
		originalDispatcher = ClientCanvasStore.renderDispatcher;
		RedfxConfig cfg = RedfxConfig.get();
		origDripOverEdges = cfg.dripOverEdges;
		origGradualSplatter = cfg.gradualSplatter;
		origGrowthDelayTicks = cfg.splatterGrowthDelayTicks;
		origLifetimeSeconds = cfg.particleLifetimeSeconds;
		cfg.gradualSplatter = true;
		cfg.splatterGrowthDelayTicks = 2;
		cfg.particleLifetimeSeconds = 5;
	}

	@AfterEach
	void tearDown() {
		ClientCanvasStore.renderDispatcher = originalDispatcher;
		store.clearAll();
		RedfxConfig cfg = RedfxConfig.get();
		cfg.dripOverEdges = origDripOverEdges;
		cfg.gradualSplatter = origGradualSplatter;
		cfg.splatterGrowthDelayTicks = origGrowthDelayTicks;
		cfg.particleLifetimeSeconds = origLifetimeSeconds;
	}

	private record BenchResult(
		String label,
		double totalStampMs,
		double maxTickStampMs,
		double totalTickMs,
		double maxTickExpireMs,
		int peakPendingStages,
		int peakPendingActions,
		int spawnedDripActions,
		int dirtySectionCalls
	) {
		void print() {
			System.out.printf(
				"[SpiderFarmBench] %-22s | stamp=%.2fms (maxTick=%.2fms) | expire=%.2fms (maxTick=%.2fms) | peakStages=%d | peakActions=%d | dripEffects=%d | dirtySections=%d%n",
				label, totalStampMs, maxTickStampMs, totalTickMs, maxTickExpireMs,
				peakPendingStages, peakPendingActions, spawnedDripActions, dirtySectionCalls
			);
		}
	}

	private static BlockGetter spiderFarmWorld() {
		Map<BlockPos, BlockState> blocks = new HashMap<>();
		// 1x2 hopper killing floor elevated over air + catch slab below
		blocks.put(new BlockPos(0, 64, 0), Blocks.HOPPER.defaultBlockState());
		blocks.put(new BlockPos(1, 64, 0), Blocks.HOPPER.defaultBlockState());
		blocks.put(new BlockPos(0, 63, 1), Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
		blocks.put(new BlockPos(1, 63, 1), Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
		// Surrounding stone wall on north side
		blocks.put(new BlockPos(0, 64, -1), Blocks.STONE.defaultBlockState());
		blocks.put(new BlockPos(1, 64, -1), Blocks.STONE.defaultBlockState());
		blocks.put(new BlockPos(0, 65, -1), Blocks.STONE.defaultBlockState());
		blocks.put(new BlockPos(1, 65, -1), Blocks.STONE.defaultBlockState());
		// Open-air stone pedestal for interior-splat edge-drip test
		blocks.put(new BlockPos(5, 64, 5), Blocks.STONE.defaultBlockState());

		return new BlockGetter() {
			@Override
			public BlockEntity getBlockEntity(BlockPos pos) {
				return null;
			}

			@Override
			public BlockState getBlockState(BlockPos pos) {
				return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
			}

			@Override
			public FluidState getFluidState(BlockPos pos) {
				return Fluids.EMPTY.defaultFluidState();
			}

			@Override
			public int getHeight() {
				return 384;
			}

			@Override
			public int getMinY() {
				return -64;
			}
		};
	}

	private BenchResult runSpiderFarmWorkload(String label, boolean dripOverEdges) {
		store.clearAll();
		RedfxConfig.get().dripOverEdges = dripOverEdges;

		AtomicInteger dirtyCalls = new AtomicInteger();
		ClientCanvasStore.renderDispatcher = new ClientCanvasStore.RenderDispatcher() {
			@Override
			public boolean isRenderThread() {
				return true;
			}

			@Override
			public void executeOnRenderThread(Runnable action) {
				action.run();
			}

			@Override
			public void markSectionDirty(int secX, int secY, int secZ) {
				dirtyCalls.incrementAndGet();
			}

			@Override
			public void markChunkDirty(int chunkX, int chunkZ) {
				dirtyCalls.incrementAndGet();
			}
		};

		AtomicInteger dripEffects = new AtomicInteger();
		BloodParticle.DripActionDispatcher actionDispatcher = new BloodParticle.DripActionDispatcher() {
			@Override
			public void spawnFloorSplash(double x, double y, double z, float r, float g, float b) {
				dripEffects.incrementAndGet();
			}

			@Override
			public void spawnOverhangDrop(double x, double y, double z, float r, float g, float b) {
				dripEffects.incrementAndGet();
			}
		};

		BlockGetter level = spiderFarmWorld();
		RandomSource rng = RandomSource.create(42L);
		BlockPos[] killingFloor = { new BlockPos(0, 64, 0), new BlockPos(1, 64, 0) };

		// Short JIT warmup (off measured region)
		for (int w = 0; w < 10; w++) {
			BlockPos p = killingFloor[w & 1];
			BloodParticle.stampToCanvas(level, p.getX() + 0.5, 65.0, p.getZ() + 0.5,
				0.05, -0.1, 0.05, 0.6f, 0.9f, 0.1f, 1 + (w % 5), rng, p, Direction.UP, actionDispatcher, 1_000L);
		}
		store.clearAll();
		dirtyCalls.set(0);
		dripEffects.set(0);

		long simTimeMs = 100_000L;
		long totalStampNs = 0L;
		long maxTickStampNs = 0L;
		long totalExpireNs = 0L;
		long maxTickExpireNs = 0L;
		int peakStages = 0;
		int peakActions = 0;

		// 160 ticks = 8 seconds of spider farm operation:
		// Ticks 0..100: cramming hits + deaths every 10 ticks; Tick 100 (5s mark): heavy 5-spider death burst (350 impacts)
		for (int tick = 0; tick < 160; tick++) {
			simTimeMs += 50L;

			int impactsThisTick = 0;
			if (tick < 120) {
				if (tick == 100) {
					impactsThisTick = 350; // ~4-5 crammed spiders dying in one wave
				} else if (tick % 10 == 0) {
					impactsThisTick = 90; // ~3-4 crammed spiders taking damage
				} else if (tick % 2 == 0) {
					impactsThisTick = 8; // trickle hits/drips
				}
			}

			long t0 = System.nanoTime();
			for (int i = 0; i < impactsThisTick; i++) {
				BlockPos target = killingFloor[i % killingFloor.length];
				double hitX = target.getX() + 0.1 + rng.nextDouble() * 0.8;
				double hitZ = target.getZ() + 0.1 + rng.nextDouble() * 0.8;
				BloodParticle.stampToCanvas(
					level, hitX, 65.0, hitZ,
					(rng.nextDouble() - 0.5) * 0.16, -0.08, (rng.nextDouble() - 0.5) * 0.16,
					0.65f, 0.90f, 0.10f, 1 + rng.nextInt(5), rng, target, Direction.UP,
					actionDispatcher, simTimeMs
				);
			}
			long stampElapsed = System.nanoTime() - t0;
			totalStampNs += stampElapsed;
			maxTickStampNs = Math.max(maxTickStampNs, stampElapsed);

			peakStages = Math.max(peakStages, store.pendingStageCount());
			peakActions = Math.max(peakActions, store.pendingActionCount());

			long t1 = System.nanoTime();
			store.tickExpiration(simTimeMs);
			long expireElapsed = System.nanoTime() - t1;
			totalExpireNs += expireElapsed;
			maxTickExpireNs = Math.max(maxTickExpireNs, expireElapsed);
		}

		BenchResult res = new BenchResult(
			label,
			totalStampNs / 1_000_000.0,
			maxTickStampNs / 1_000_000.0,
			totalExpireNs / 1_000_000.0,
			maxTickExpireNs / 1_000_000.0,
			peakStages,
			peakActions,
			dripEffects.get(),
			dirtyCalls.get()
		);
		res.print();
		return res;
	}

	@Test
	void benchmarkSpiderFarmWithAndWithoutEdgeDripping() {
		BenchResult noDrip = runSpiderFarmWorkload("dripOverEdges=OFF", false);
		BenchResult withDrip = runSpiderFarmWorkload("dripOverEdges=ON", true);
		org.junit.jupiter.api.Assertions.assertTrue(noDrip.totalStampMs >= 0.0);
		org.junit.jupiter.api.Assertions.assertTrue(withDrip.spawnedDripActions > 0,
			"Edge dripping must still emit overhang/splash drops for genuine rim hits");
		org.junit.jupiter.api.Assertions.assertTrue(withDrip.peakPendingStages <= 250,
			"Pending stages should remain coalesced under spider farm bursts, was " + withDrip.peakPendingStages);
		org.junit.jupiter.api.Assertions.assertTrue(withDrip.peakPendingActions <= 512,
			"Pending drip actions must stay capped, was " + withDrip.peakPendingActions);
		org.junit.jupiter.api.Assertions.assertTrue(withDrip.spawnedDripActions <= 800,
			"Edge drip particle actions must not multiply uncontrollably on repeated hits, was " + withDrip.spawnedDripActions);
	}

	@Test
	void alreadyPaintedEdgesDoNotRedripWhenInteriorIsHit() {
		RedfxConfig.get().dripOverEdges = true;
		ClientCanvasStore.renderDispatcher = new ClientCanvasStore.RenderDispatcher() {
			@Override public boolean isRenderThread() { return true; }
			@Override public void executeOnRenderThread(Runnable action) { action.run(); }
			@Override public void markSectionDirty(int secX, int secY, int secZ) {}
			@Override public void markChunkDirty(int chunkX, int chunkZ) {}
		};

		// Pre-stain all 4 perimeter edges of open-air stone block (5, 64, 5, UP)
		BlockPos fullStone = new BlockPos(5, 64, 5);
		int[] preStained = new int[com.drinfonty.redfx.canvas.Canvas.TEXELS];
		for (int i = 0; i < 16; i++) {
			preStained[i] = 0xFFFF0000;
			preStained[15 * 16 + i] = 0xFFFF0000;
			preStained[i * 16] = 0xFFFF0000;
			preStained[i * 16 + 15] = 0xFFFF0000;
		}
		store.put(fullStone, com.drinfonty.redfx.canvas.FaceAxes.UP, new com.drinfonty.redfx.canvas.Canvas(preStained, 200_000L, 200_000L));

		AtomicInteger dripEffects = new AtomicInteger();
		BloodParticle.DripActionDispatcher dispatcher = new BloodParticle.DripActionDispatcher() {
			@Override public void spawnFloorSplash(double x, double y, double z, float r, float g, float b) { dripEffects.incrementAndGet(); }
			@Override public void spawnOverhangDrop(double x, double y, double z, float r, float g, float b) { dripEffects.incrementAndGet(); }
		};

		BlockGetter level = spiderFarmWorld();
		RandomSource rng = RandomSource.create(7L);
		long t = 100_000L;
		// Hit strictly at the center (5.5, 65.0, 5.5) on a solid stone block far from edges
		RedfxConfig.get().splatSizeScale = 0.5f;
		try {
			BloodParticle.stampToCanvas(level, 5.5, 65.0, 5.5, 0.0, -0.05, 0.0, 1.0f, 0.0f, 0.0f, 1, rng, fullStone, Direction.UP, dispatcher, t);
			for (int tick = 0; tick < 60; tick++) {
				t += 50L;
				store.tickExpiration(t);
			}
			org.junit.jupiter.api.Assertions.assertEquals(0, dripEffects.get(),
				"Pre-existing edge blood must NOT re-trigger drips when new splatter only hits block interior");
		} finally {
			RedfxConfig.get().splatSizeScale = 1.0f;
		}
	}

	@Test
	void stampBudgetCapsBurstImpactsOnSameBlockFace() {
		RedfxConfig.get().dripOverEdges = true;
		ClientCanvasStore.renderDispatcher = new ClientCanvasStore.RenderDispatcher() {
			@Override public boolean isRenderThread() { return true; }
			@Override public void executeOnRenderThread(Runnable action) { action.run(); }
			@Override public void markSectionDirty(int secX, int secY, int secZ) {}
			@Override public void markChunkDirty(int chunkX, int chunkZ) {}
		};
		BloodParticle.DripActionDispatcher noop = new BloodParticle.DripActionDispatcher() {
			@Override public void spawnFloorSplash(double x, double y, double z, float r, float g, float b) {}
			@Override public void spawnOverhangDrop(double x, double y, double z, float r, float g, float b) {}
		};

		BlockGetter level = spiderFarmWorld();
		BlockPos target = new BlockPos(0, 64, 0);
		RandomSource rng = RandomSource.create(99L);
		long t = 100_000L;

		int acceptedFirstWindow = 0;
		for (int i = 0; i < 100; i++) {
			if (BloodParticle.stampToCanvas(level, 0.5, 65.0, 0.5, 0.0, -0.1, 0.0, 1.0f, 0.0f, 0.0f, 1, rng, target, Direction.UP, noop, t)) {
				acceptedFirstWindow++;
			}
		}
		org.junit.jupiter.api.Assertions.assertEquals(16, acceptedFirstWindow,
			"Per-face 100ms window should cap redundant burst stamps to 16");

		// Window resets cleanly after 100ms
		org.junit.jupiter.api.Assertions.assertTrue(
			BloodParticle.stampToCanvas(level, 0.5, 65.0, 0.5, 0.0, -0.1, 0.0, 1.0f, 0.0f, 0.0f, 1, rng, target, Direction.UP, noop, t + 100L),
			"Face stamp budget should refill after 100ms window");
	}
}
