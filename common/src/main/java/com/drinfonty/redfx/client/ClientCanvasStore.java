package com.drinfonty.redfx.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.drinfonty.redfx.canvas.BloodSplatter;
import com.drinfonty.redfx.canvas.Canvas;
import com.drinfonty.redfx.canvas.CanvasKey;
import com.drinfonty.redfx.canvas.FaceAxes;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/**
 * Client-side canvas store read concurrently by chunk-mesher worker threads.
 * Canvases and chunk maps are copy-on-write so readers never see torn data.
 */
public final class ClientCanvasStore {
	private static final ClientCanvasStore INSTANCE = new ClientCanvasStore();

	public static long chunkKey(int chunkX, int chunkZ) {
		return (((long) chunkX) & 0xFFFFFFFFL) | ((((long) chunkZ) & 0xFFFFFFFFL) << 32);
	}

	public static long chunkKey(BlockPos pos) {
		return chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
	}

	private static int chunkX(long chunkKey) {
		return (int) (chunkKey & 0xFFFFFFFFL);
	}

	private static int chunkZ(long chunkKey) {
		return (int) (chunkKey >>> 32);
	}

	private static long packSection(int secX, int secY, int secZ) {
		return (((long) secX & 0x3FFFFFFL) << 38) | ((((long) secY) & 0xFFFL) << 26) | (((long) secZ) & 0x3FFFFFFL);
	}

	private static int unpackSectionX(long packed) {
		return (int) (packed >> 38);
	}

	private static int unpackSectionY(long packed) {
		return (int) ((packed << 26) >> 52);
	}

	private static int unpackSectionZ(long packed) {
		return (int) ((packed << 38) >> 38);
	}

	/** Interval between erosion steps (200ms = 5 times per second). */
	private static final long ERODE_INTERVAL_MS = 200L;
	/** Number of edge pixels erased per step. */
	private static final int ERODE_PIXELS_PER_STEP = 3;

	public static ClientCanvasStore get() {
		return INSTANCE;
	}

	public record PendingTexel(BlockPos pos, int face, int u, int v, int col, long expirationMs) {
	}

	private record PendingStage(long executeAtMs, List<PendingTexel> texels) {
	}

	private final ConcurrentHashMap<Long, Long2ObjectMap<Canvas>> chunks = new ConcurrentHashMap<>();
	private final List<PendingStage> pendingStages = new ArrayList<>();

	private ClientCanvasStore() {
	}

	public Canvas get(BlockPos pos, int face) {
		Long2ObjectMap<Canvas> canvases = chunks.get(chunkKey(pos));
		if (canvases == null) {
			return null;
		}
		return canvases.get(CanvasKey.pack(pos.getX() & 0xF, pos.getY(), pos.getZ() & 0xF, face));
	}

	public boolean isPainted(BlockPos pos) {
		Long2ObjectMap<Canvas> canvases = chunks.get(chunkKey(pos));
		if (canvases == null || canvases.isEmpty()) {
			return false;
		}
		int lx = pos.getX() & 0xF;
		int y = pos.getY();
		int lz = pos.getZ() & 0xF;

		for (int face = 0; face < FaceAxes.FACE_COUNT; face++) {
			if (canvases.containsKey(CanvasKey.pack(lx, y, lz, face))) {
				return true;
			}
		}
		return false;
	}

	public boolean hasAnyBlood() {
		return !chunks.isEmpty() || !pendingStages.isEmpty();
	}

	public synchronized void scheduleStage(long executeAtMs, List<PendingTexel> texels) {
		if (texels == null || texels.isEmpty()) {
			return;
		}
		pendingStages.add(new PendingStage(executeAtMs, texels));
	}

	public synchronized void applyTexels(List<PendingTexel> texels) {
		if (texels == null || texels.isEmpty()) {
			return;
		}
		LongOpenHashSet toDirtySections = new LongOpenHashSet();
		applyTexelsInternal(texels, toDirtySections);
		for (long secKey : toDirtySections) {
			dirtySectionCoord(unpackSectionX(secKey), unpackSectionY(secKey), unpackSectionZ(secKey));
		}
	}

	private void applyTexelsInternal(List<PendingTexel> texels, LongOpenHashSet toDirtySections) {
		Map<Long, Map<Long, List<PendingTexel>>> grouped = new java.util.HashMap<>();
		for (PendingTexel pt : texels) {
			if (pt.u < 0 || pt.u >= Canvas.SIZE || pt.v < 0 || pt.v >= Canvas.SIZE) continue;
			long cKey = chunkKey(pt.pos);
			long canKey = CanvasKey.pack(pt.pos.getX() & 0xF, pt.pos.getY(), pt.pos.getZ() & 0xF, pt.face);
			grouped.computeIfAbsent(cKey, k -> new java.util.HashMap<>())
				.computeIfAbsent(canKey, k -> new ArrayList<>())
				.add(pt);
		}

		for (Map.Entry<Long, Map<Long, List<PendingTexel>>> chunkEntry : grouped.entrySet()) {
			long cKey = chunkEntry.getKey();
			Long2ObjectMap<Canvas> current = chunks.get(cKey);
			Long2ObjectOpenHashMap<Canvas> next = current == null
				? new Long2ObjectOpenHashMap<>()
				: new Long2ObjectOpenHashMap<>(current);

			boolean chunkChanged = false;

			for (Map.Entry<Long, List<PendingTexel>> canEntry : chunkEntry.getValue().entrySet()) {
				long canKey = canEntry.getKey();
				Canvas existingCanvas = next.get(canKey);
				int[] canvasTexels = existingCanvas != null ? existingCanvas.texels().clone() : new int[Canvas.TEXELS];
				long fadeStart = existingCanvas != null ? existingCanvas.fadeStartTimeMs() : 0L;
				long nextErode = existingCanvas != null ? existingCanvas.nextErodeTimeMs() : 0L;
				boolean canvasChanged = false;

				for (PendingTexel pt : canEntry.getValue()) {
					int idx = pt.v * Canvas.SIZE + pt.u;
					int existing = canvasTexels[idx];
					int existingAlpha = (existing >>> 24);
					int newAlpha = (pt.col >>> 24);

					if (newAlpha > existingAlpha || (newAlpha == existingAlpha && existing != pt.col)) {
						canvasTexels[idx] = pt.col;
						canvasChanged = true;
						if (pt.expirationMs > fadeStart) {
							fadeStart = pt.expirationMs;
							nextErode = fadeStart;
						}
					}
				}

				if (canvasChanged) {
					chunkChanged = true;
					next.put(canKey, new Canvas(canvasTexels, fadeStart, nextErode));

					int chunkX = chunkX(cKey);
					int chunkZ = chunkZ(cKey);
					int secY = CanvasKey.y(canKey) >> 4;
					toDirtySections.add(packSection(chunkX, secY, chunkZ));
				}
			}

			if (chunkChanged) {
				chunks.put(cKey, Long2ObjectMaps.unmodifiable(next));
			}
		}
	}

	public synchronized int pendingStageCount() {
		return pendingStages.size();
	}

	public synchronized void put(BlockPos pos, int face, Canvas canvas) {
		long chunkKey = chunkKey(pos);
		long key = CanvasKey.pack(pos.getX() & 0xF, pos.getY(), pos.getZ() & 0xF, face);

		Long2ObjectMap<Canvas> current = chunks.get(chunkKey);
		Long2ObjectOpenHashMap<Canvas> next = current == null
			? new Long2ObjectOpenHashMap<>()
			: new Long2ObjectOpenHashMap<>(current);

		if (canvas == null || canvas.isEmpty()) {
			next.remove(key);
		} else {
			next.put(key, canvas);
		}

		if (next.isEmpty()) {
			chunks.remove(chunkKey);
		} else {
			chunks.put(chunkKey, Long2ObjectMaps.unmodifiable(next));
		}

		dirtySection(pos);
	}

	public synchronized void clearBlock(BlockPos pos) {
		long chunkKey = chunkKey(pos);
		Long2ObjectMap<Canvas> current = chunks.get(chunkKey);
		if (current == null || current.isEmpty()) {
			return;
		}

		int lx = pos.getX() & 0xF;
		int y = pos.getY();
		int lz = pos.getZ() & 0xF;
		boolean changed = false;
		Long2ObjectOpenHashMap<Canvas> next = new Long2ObjectOpenHashMap<>(current);

		for (int face = 0; face < FaceAxes.FACE_COUNT; face++) {
			long key = CanvasKey.pack(lx, y, lz, face);
			if (next.remove(key) != null) {
				changed = true;
			}
		}

		if (changed) {
			if (next.isEmpty()) {
				chunks.remove(chunkKey);
			} else {
				chunks.put(chunkKey, Long2ObjectMaps.unmodifiable(next));
			}
			dirtySection(pos);
		}
	}

	public synchronized void clearChunk(int chunkX, int chunkZ) {
		clearChunk(chunkKey(chunkX, chunkZ), true);
	}

	public synchronized void clearChunk(int chunkX, int chunkZ, boolean dirtyRender) {
		clearChunk(chunkKey(chunkX, chunkZ), dirtyRender);
	}

	public synchronized void clearChunk(long chunkPosPacked) {
		clearChunk(chunkPosPacked, true);
	}

	public synchronized void clearChunk(long chunkPosPacked, boolean dirtyRender) {
		Long2ObjectMap<Canvas> removed = chunks.remove(chunkPosPacked);
		pendingStages.removeIf(stage -> {
			stage.texels.removeIf(pt -> chunkKey(pt.pos) == chunkPosPacked);
			return stage.texels.isEmpty();
		});
		if (removed != null && !removed.isEmpty() && dirtyRender) {
			int chunkX = chunkX(chunkPosPacked);
			int chunkZ = chunkZ(chunkPosPacked);
			dirtyChunk(chunkX, chunkZ);
		}
	}

	public synchronized void clearAll() {
		chunks.clear();
		pendingStages.clear();
	}

	/**
	 * Ticked every client tick to slowly erode and dissolve decals that have passed their fade start time,
	 * and apply scheduled splatter expansion stages.
	 */
	public synchronized void tickExpiration(long nowMs) {
		if (chunks.isEmpty() && pendingStages.isEmpty()) {
			return;
		}

		LongOpenHashSet toDirtySections = new LongOpenHashSet();

		if (!pendingStages.isEmpty()) {
			Iterator<PendingStage> pIt = pendingStages.iterator();
			List<PendingTexel> readyTexels = new ArrayList<>();
			while (pIt.hasNext()) {
				PendingStage stage = pIt.next();
				if (nowMs >= stage.executeAtMs) {
					pIt.remove();
					readyTexels.addAll(stage.texels);
				}
			}
			if (!readyTexels.isEmpty()) {
				applyTexelsInternal(readyTexels, toDirtySections);
			}
		}

		if (!chunks.isEmpty()) {
			Iterator<Map.Entry<Long, Long2ObjectMap<Canvas>>> it = chunks.entrySet().iterator();

			while (it.hasNext()) {
				Map.Entry<Long, Long2ObjectMap<Canvas>> entry = it.next();
				Long2ObjectMap<Canvas> map = entry.getValue();
				Long2ObjectOpenHashMap<Canvas> next = null;

				for (Long2ObjectMap.Entry<Canvas> cEntry : map.long2ObjectEntrySet()) {
					Canvas c = cEntry.getValue();
					if (c == null || c.fadeStartTimeMs() <= 0) {
						continue;
					}

					if (nowMs >= c.fadeStartTimeMs() && nowMs >= c.nextErodeTimeMs()) {
						long key = cEntry.getLongKey();
						int[] texels = c.texels().clone();
						int erased = BloodSplatter.erode(texels, ERODE_PIXELS_PER_STEP);

						if (next == null) {
							next = new Long2ObjectOpenHashMap<>(map);
						}

						boolean empty = true;
						for (int t : texels) {
							if (t != 0) {
								empty = false;
								break;
							}
						}

						if (empty || erased == 0) {
							next.remove(key);
						} else {
							Canvas erodedCanvas = new Canvas(texels, c.fadeStartTimeMs(), nowMs + ERODE_INTERVAL_MS);
							next.put(key, erodedCanvas);
						}

						long chunkKey = entry.getKey();
						int chunkX = chunkX(chunkKey);
						int chunkZ = chunkZ(chunkKey);
						int secY = CanvasKey.y(key) >> 4;
						toDirtySections.add(packSection(chunkX, secY, chunkZ));
					}
				}

				if (next != null) {
					if (next.isEmpty()) {
						it.remove();
					} else {
						entry.setValue(Long2ObjectMaps.unmodifiable(next));
					}
				}
			}
		}

		for (long secKey : toDirtySections) {
			dirtySectionCoord(unpackSectionX(secKey), unpackSectionY(secKey), unpackSectionZ(secKey));
		}
	}

	public interface RenderDispatcher {
		boolean isRenderThread();
		void executeOnRenderThread(Runnable action);
		void markSectionDirty(int secX, int secY, int secZ);
		void markChunkDirty(int chunkX, int chunkZ);
	}

	private static final RenderDispatcher DEFAULT_DISPATCHER = new RenderDispatcher() {
		private java.lang.reflect.Method minSectionMethod;
		private java.lang.reflect.Method maxSectionMethod;
		private boolean useBuildHeightFallback = false;
		private boolean initialized = false;

		private synchronized void initHeightMethods(Class<?> levelClass) {
			if (initialized) return;
			try {
				minSectionMethod = levelClass.getMethod("getMinSectionY");
				maxSectionMethod = levelClass.getMethod("getMaxSectionY");
			} catch (Throwable t) {
				try {
					minSectionMethod = levelClass.getMethod("getMinBuildHeight");
					maxSectionMethod = levelClass.getMethod("getMaxBuildHeight");
					useBuildHeightFallback = true;
				} catch (Throwable ignored) {
				}
			}
			initialized = true;
		}

		private int getMinSection(Object level) {
			if (!initialized) initHeightMethods(level.getClass());
			if (minSectionMethod != null) {
				try {
					int val = (int) minSectionMethod.invoke(level);
					return useBuildHeightFallback ? (val >> 4) : val;
				} catch (Throwable ignored) {}
			}
			return -4;
		}

		private int getMaxSection(Object level) {
			if (!initialized) initHeightMethods(level.getClass());
			if (maxSectionMethod != null) {
				try {
					int val = (int) maxSectionMethod.invoke(level);
					return useBuildHeightFallback ? (val >> 4) : val;
				} catch (Throwable ignored) {}
			}
			return 20;
		}

		@Override
		public boolean isRenderThread() {
			Minecraft mc = Minecraft.getInstance();
			return mc == null || mc.isSameThread();
		}

		@Override
		public void executeOnRenderThread(Runnable action) {
			Minecraft mc = Minecraft.getInstance();
			if (mc != null) {
				mc.execute(action);
			}
		}

		@Override
		public void markSectionDirty(int secX, int secY, int secZ) {
			Minecraft mc = Minecraft.getInstance();
			if (mc != null && mc.level != null) {
				mc.level.setSectionDirtyWithNeighbors(secX, secY, secZ);
			}
		}

		@Override
		public void markChunkDirty(int chunkX, int chunkZ) {
			Minecraft mc = Minecraft.getInstance();
			if (mc != null && mc.level != null) {
				int minSection = getMinSection(mc.level);
				int maxSection = getMaxSection(mc.level);
				for (int sy = minSection; sy < maxSection; sy++) {
					mc.level.setSectionDirtyWithNeighbors(chunkX, sy, chunkZ);
				}
			}
		}
	};

	static volatile RenderDispatcher renderDispatcher = DEFAULT_DISPATCHER;

	private void dirtySection(BlockPos pos) {
		dirtySectionCoord(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
	}

	private void dirtySectionCoord(int secX, int secY, int secZ) {
		RenderDispatcher dispatcher = renderDispatcher;
		if (!dispatcher.isRenderThread()) {
			dispatcher.executeOnRenderThread(() -> dirtySectionCoord(secX, secY, secZ));
			return;
		}
		dispatcher.markSectionDirty(secX, secY, secZ);
	}

	private void dirtyChunk(int chunkX, int chunkZ) {
		RenderDispatcher dispatcher = renderDispatcher;
		if (!dispatcher.isRenderThread()) {
			dispatcher.executeOnRenderThread(() -> dirtyChunk(chunkX, chunkZ));
			return;
		}
		dispatcher.markChunkDirty(chunkX, chunkZ);
	}
}
