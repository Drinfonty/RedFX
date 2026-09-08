package com.drinfonty.redfx.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.drinfonty.redfx.canvas.Canvas;
import com.drinfonty.redfx.canvas.FaceAxes;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;

class ClientCanvasStoreThreadSafetyTest {
	private ClientCanvasStore store;
	private ClientCanvasStore.RenderDispatcher originalDispatcher;

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
	}

	@AfterEach
	void tearDown() {
		ClientCanvasStore.renderDispatcher = originalDispatcher;
		store.clearAll();
	}

	/**
	 * Simulates Sodium/Vanilla thread assertion:
	 * If rendering dirty state is accessed outside the render thread, it must throw IllegalStateException.
	 */
	@Test
	void testServerThreadClearBlockDispatchesToRenderThread() throws InterruptedException {
		ConcurrentLinkedQueue<Runnable> renderQueue = new ConcurrentLinkedQueue<>();
		AtomicInteger sectionDirtyCalls = new AtomicInteger(0);
		AtomicReference<String> sectionDirtyThread = new AtomicReference<>();

		ClientCanvasStore.renderDispatcher = new ClientCanvasStore.RenderDispatcher() {
			@Override
			public boolean isRenderThread() {
				return "Render thread".equals(Thread.currentThread().getName());
			}

			@Override
			public void executeOnRenderThread(Runnable action) {
				renderQueue.add(action);
			}

			@Override
			public void markSectionDirty(int secX, int secY, int secZ) {
				if (!isRenderThread()) {
					throw new IllegalStateException("Tried to access render state from outside the main render thread! Current thread: " + Thread.currentThread().getName());
				}
				sectionDirtyCalls.incrementAndGet();
				sectionDirtyThread.set(Thread.currentThread().getName());
			}

			@Override
			public void markChunkDirty(int chunkX, int chunkZ) {
				if (!isRenderThread()) {
					throw new IllegalStateException("Tried to access render state from outside the main render thread! Current thread: " + Thread.currentThread().getName());
				}
			}
		};

		// 1. Paint a block at (10, 64, 10)
		BlockPos pos = new BlockPos(10, 64, 10);
		int[] texels = new int[Canvas.TEXELS];
		texels[0] = 0xFFFF0000;
		Canvas canvas = new Canvas(texels, 10000L, 10200L);
		store.put(pos, FaceAxes.UP, canvas);
		assertTrue(store.isPainted(pos));

		// Clear initial put dirty queue
		renderQueue.clear();

		// 2. Simulate server thread (e.g. Wither explosion, TNT, mob tick) breaking the block
		AtomicReference<Throwable> serverError = new AtomicReference<>();
		Thread serverThread = new Thread(() -> {
			try {
				store.clearBlock(pos);
			} catch (Throwable t) {
				serverError.set(t);
			}
		}, "Server thread");

		serverThread.start();
		serverThread.join(3000);

		// The server thread MUST NOT throw an IllegalStateException or crash
		if (serverError.get() != null) {
			throw new AssertionError("Server thread crashed when clearing block decals", serverError.get());
		}

		// The decal is removed from the store immediately
		assertFalse(store.isPainted(pos));

		// Dirtying must NOT have happened synchronously on the server thread
		assertEquals(0, sectionDirtyCalls.get());
		assertEquals(1, renderQueue.size(), "Render dirty action must be queued for render thread");

		// 3. Now execute on the simulated Render thread
		AtomicReference<Throwable> renderError = new AtomicReference<>();
		Thread renderRunner = new Thread(() -> {
			try {
				Runnable action;
				while ((action = renderQueue.poll()) != null) {
					action.run();
				}
			} catch (Throwable t) {
				renderError.set(t);
			}
		}, "Render thread");

		// Point our simulated renderThread reference to renderRunner
		renderRunner.start();
		renderRunner.join(3000);

		if (renderError.get() != null) {
			throw new AssertionError("Render thread failed while processing queued dirty action", renderError.get());
		}

		// Render state was safely dirtied
		assertEquals(1, sectionDirtyCalls.get());
	}

	@Test
	void testServerThreadClearChunkDispatchesToRenderThread() throws InterruptedException {
		ConcurrentLinkedQueue<Runnable> renderQueue = new ConcurrentLinkedQueue<>();
		AtomicInteger chunkDirtyCalls = new AtomicInteger(0);
		AtomicBoolean onRenderThread = new AtomicBoolean(false);

		ClientCanvasStore.renderDispatcher = new ClientCanvasStore.RenderDispatcher() {
			@Override
			public boolean isRenderThread() {
				return onRenderThread.get();
			}

			@Override
			public void executeOnRenderThread(Runnable action) {
				renderQueue.add(action);
			}

			@Override
			public void markSectionDirty(int secX, int secY, int secZ) {
			}

			@Override
			public void markChunkDirty(int chunkX, int chunkZ) {
				if (!isRenderThread()) {
					throw new IllegalStateException("Tried to access render state from outside the main render thread! Current thread: " + Thread.currentThread().getName());
				}
				chunkDirtyCalls.incrementAndGet();
			}
		};

		BlockPos pos = new BlockPos(10, 64, 10);
		int[] texels = new int[Canvas.TEXELS];
		texels[0] = 0xFFFF0000;
		store.put(pos, FaceAxes.UP, new Canvas(texels, 10000L, 10200L));
		renderQueue.clear();

		// Pack chunk key (x >> 4, z >> 4) in a version-neutral manner
		long chunkKey = (((long) (pos.getX() >> 4)) & 0xFFFFFFFFL) | ((((long) (pos.getZ() >> 4)) & 0xFFFFFFFFL) << 32);

		AtomicReference<Throwable> serverError = new AtomicReference<>();
		Thread serverThread = new Thread(() -> {
			try {
				store.clearChunk(chunkKey);
			} catch (Throwable t) {
				serverError.set(t);
			}
		}, "Server thread");

		serverThread.start();
		serverThread.join(3000);

		if (serverError.get() != null) {
			throw new AssertionError("Server thread crashed when clearing chunk decals", serverError.get());
		}

		assertEquals(0, chunkDirtyCalls.get());
		assertEquals(1, renderQueue.size());

		// Drain on render thread
		onRenderThread.set(true);
		Runnable action = renderQueue.poll();
		if (action != null) {
			action.run();
		}
		assertEquals(1, chunkDirtyCalls.get());
	}

	@Test
	void testConcurrentMultiThreadedStoreOperations() throws InterruptedException {
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
			}

			@Override
			public void markChunkDirty(int chunkX, int chunkZ) {
			}
		};

		int threads = 8;
		int iterations = 2000;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch latch = new CountDownLatch(threads);
		List<Throwable> errors = new ArrayList<>();

		for (int t = 0; t < threads; t++) {
			final int threadId = t;
			pool.submit(() -> {
				try {
					for (int i = 0; i < iterations; i++) {
						BlockPos pos = new BlockPos(i % 16, 64 + (threadId % 4), i % 16);
						if (threadId % 2 == 0) {
							// Writer
							int[] texels = new int[Canvas.TEXELS];
							texels[0] = 0xFFFF0000;
							store.put(pos, FaceAxes.UP, new Canvas(texels, 5000L, 5200L));
							store.isPainted(pos);
							if (i % 3 == 0) {
								store.clearBlock(pos);
							}
						} else {
							// Reader / Mesher
							store.get(pos, FaceAxes.UP);
							store.isPainted(pos);
							if (i % 10 == 0) {
								store.tickExpiration(System.currentTimeMillis());
							}
						}
					}
				} catch (Throwable err) {
					synchronized (errors) {
						errors.add(err);
					}
				} finally {
					latch.countDown();
				}
			});
		}

		boolean done = latch.await(10, TimeUnit.SECONDS);
		pool.shutdown();
		assertTrue(done, "Concurrent operations did not finish within timeout (possible deadlock)");
		assertTrue(errors.isEmpty(), "Encountered exceptions in concurrent store operations: " + errors);
	}
}
